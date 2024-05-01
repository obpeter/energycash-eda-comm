package at.energydash
package domain.stream

import actor.TenantProvider.DistributeMail
import actor.{EmailCommand, MessageStorage, PrepareMessageActor}
import config.Config
import domain.eda.message.MessageHelper
import domain.eda.message.MessageHelper.EDAMessageCodeToProcessCode
import domain.email.EmailService
import model.EbMsMessage
import model.enums.EbMsProcessType
import mqtt.path.MqttPaths

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.alpakka.mqtt.scaladsl.{MqttSink, MqttSource}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.stream.{ActorAttributes, Supervision}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.Logger

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

class MqttRequestStream(mailService: ActorRef[EmailCommand],
                        messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
                        messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
                       (implicit system: ActorSystem[_]) extends MqttPaths {

  import model.JsonImplicit._

  implicit val timeout: Timeout = Timeout(15.seconds)
  implicit val ec: ExecutionContextExecutor = system.executionContext
//  var logger: Logger = LoggerFactory.getLogger(classOf[MqttRequestStream])
  var logger: Logger = system.log

  private case class MqttException(errorMsg: MqttMessage, s: String = "", cause: Option[Throwable] = None) extends RuntimeException(s) {
    cause.foreach(initCause)
  }

  private val extractTenantFromTopic = (topic: String) => topic.split("/")(3)

  //  private val decodingFlow: Flow[String, Either[MqttMessage, EbMsMessage], NotUsed] = {
  //    Flow.fromFunction(msg => decode[EbMsMessage](msg).left.map(error => {
  //      MqttMessage(s"${Config.errorTopic}", ByteString(error.getMessage))
  //    })
  //    )
  //  }
  private val decodingFlow: Flow[String, EbMsMessage, NotUsed] = {
    Flow.fromFunction(msg => decode[EbMsMessage](msg) match {
      case Right(m) => m
      case Left(error) => throw MqttException(MqttMessage(s"${Config.errorTopic}", ByteString(error.getMessage)), error.getMessage)
    })
  }

  private val prepareMessageFlow: Flow[EbMsMessage, EbMsMessage, NotUsed] =
    ActorFlow.ask(messageTransformer)((m: EbMsMessage, replyTo: ActorRef[PrepareMessageActor.CommandReply]) =>
      PrepareMessageActor.PrepareMessage(m, replyTo)
    ).collect {
      case PrepareMessageActor.Prepared(message) =>
        message
    }

  private val storeMessageFlow: Flow[EbMsMessage, MqttMessage, NotUsed] =
    ActorFlow.ask(messageStore)(MessageStorage.AddMessage).collect {
      case MessageStorage.Added(id) => Try {
        edaReqResPath(id.sender, EDAMessageCodeToProcessCode(id.messageCode).toString)
      } fold(
        exc => throw exc,
        topic => {
          MqttMessage(
            topic,
            ByteString(id.asJson.toString()))
            .withQos(MqttQoS.atMostOnce).withRetained(false)
        })
    }

  private def buildHeader(data: EbMsMessage) = {

    val msgCode = EDAMessageCodeToProcessCode(data.messageCode)
    val msgCodeVersion: Option[String] = msgCode match {
      case EbMsProcessType.PROCESS_EC_PRTFACT_CHANGE => Some("_01.00")
      case _ if data.messageCodeVersion.isDefined => data.messageCodeVersion.map("_" + _)
      case _ => None
    }
    s"[${msgCode}${
      msgCodeVersion match {
        case Some(v) => v
        case None => ""
      }
    } MessageId=${data.messageId.getOrElse("")}]"
  }

  private val prepareEmailMessageFlow: Flow[EbMsMessage, EmailService.EmailModel, NotUsed] =
    Flow.fromFunction(data => {
      MessageHelper.getEdaMessageByType(data).toByte.fold(
        e => throw e,
        attachment => {
          //          val subject = s"[${EDAMessageCodeToProcessCode(data.messageCode).toString}${if(data.messageCodeVersion.isDefined) "_"+data.messageCodeVersion else ""} MessageId=${data.messageId.getOrElse("")}]"
          val subject = buildHeader(data)
          val to = data.receiver.toUpperCase()
          val tenant = data.sender.toUpperCase()

          logger.debug(s"Prepare Email Message Flow: $data")
          EmailService.EmailModel(tenant = tenant, toEmail = to,
            subject = subject, attachment = attachment, data = data)
        }
      )
    })

  private def commandFlow(input: String): Future[MqttMessage] = {
    Source.single[String](input)
      .via(decodingFlow)
      .via(prepareMessageFlow)
      .via(prepareEmailMessageFlow)
      .via(
        ActorFlow.ask(parallelism = 1)(mailService)((msg: EmailService.EmailModel, replyTo: ActorRef[EmailCommand]) =>
          DistributeMail(msg.tenant, msg, replyTo)).collect {
          case EmailService.SendEmailResponse(msg) => msg
          case err: EmailService.SendErrorResponse =>
            throw MqttException(MqttMessage(edaReqResPath(err.tenant, "error"), ByteString(err.asJson.toString().strip())), err.message)
        }
      )
      .via(storeMessageFlow)
      .recover {
        case me: MqttException =>
          logger.error(s"Error Stream handling - ${me.getMessage}")
          me.errorMsg.withQos(MqttQoS.AtMostOnce)
        case e =>
          logger.error(s"Recover: ${e.getMessage}")
          MqttMessage("eda/response/error", ByteString(e.getMessage))
      }.runWith(Sink.head[MqttMessage])
  }

  def startCommand(): Future[Done] = runCommand(MqttRequestStream.mqttSource, MqttRequestStream.mqttSink)

  def runCommand( source: Source[MqttMessage, Future[Done]], responseSink: Sink[MqttMessage, _]): Future[Done] = {
    implicit val timeout: Timeout = Timeout(15.seconds)

    val decider: Supervision.Decider = {
      case e: MqttException => {
        logger.error(s"Supervision Decider - Resume $e")
        Supervision.Resume
      }
      case _: RuntimeException => Supervision.Resume
      case _ => Supervision.Stop
    }

    source
      .map(msg => msg.payload.utf8String)
      .mapAsync(1)(commandFlow)
      .map(_.withQos(MqttQoS.atLeastOnce))
      .to(responseSink)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()
  }
}


object MqttRequestStream {
  def apply(mailService: ActorRef[EmailCommand],
            messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
            messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
           (implicit system: ActorSystem[_]): MqttRequestStream = {
    new MqttRequestStream(mailService, messageTransformer, messageStore)
  }

  private val consumerSettings =
    MqttConnectionSettings(
      Config.getMqttMailConfig.url,
      Config.getMqttMailConfig.consumerId,
      new MemoryPersistence)
      .withAutomaticReconnect(true)
      .withCleanSession(false)

  private def mqttSource: Source[MqttMessage, Future[Done]] = MqttSource.atMostOnce(
    consumerSettings,
    MqttSubscriptions(Map(Config.getMqttMailConfig.topic -> MqttQoS.AtLeastOnce)),
    bufferSize = 20
  )

  private def mqttSink: Sink[MqttMessage, Future[Done]] =
    MqttSink(
      MqttRequestStream.consumerSettings.withClientId(clientId = s"${Config.getMqttMailConfig.consumerId}/pong"),
      MqttQoS.atLeastOnce)
}
