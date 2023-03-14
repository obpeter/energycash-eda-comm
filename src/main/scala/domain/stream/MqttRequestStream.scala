package at.energydash
package domain.stream

import akka.actor.typed.scaladsl.ActorContext
import at.energydash.domain.email.{ConfiguredMailer, EmailService}
import at.energydash.domain.eda.message.MessageHelper
import at.energydash.model.EbMsMessage
import at.energydash.actor.{MessageStorage, PrepareMessageActor}
import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.alpakka.mqtt.MqttMessage
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.{ByteString, Timeout}
import at.energydash.actor.TenantProvider.DistributeMail
import at.energydash.actor.commands.{Command, EmailCommand}
import at.energydash.config.Config
import at.energydash.domain.eda.message.MessageHelper.EDAMessageCodeToProcessCode
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class MqttRequestStream(mailService: ActorRef[EmailCommand],
                             messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
                             messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
                            (implicit system: ActorSystem[_]){

  import at.energydash.model.JsonImplicit._

  implicit val timeout: Timeout = Timeout(15.seconds)
  implicit val ec = system.executionContext
  var logger = LoggerFactory.getLogger(classOf[MqttRequestStream])


  private val decodingFlow: Flow[String, Either[MqttMessage, EbMsMessage], NotUsed] = {
    Flow.fromFunction(msg => decode[EbMsMessage](msg).left.map(error =>
      MqttMessage("eda/response/error", ByteString(error.getMessage)))
    )
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
      case MessageStorage.Added(id) => MqttMessage(s"eda/response/${id.sender}", ByteString(id.asJson.toString()))
    }

  private val prepareEmailMessageFlow: Flow[EbMsMessage, EmailService.EmailModel, NotUsed] =
    Flow.fromFunction(data => {
      val attachment = MessageHelper.getEdaMessageByType(data).toByte
      val subject = s"[${EDAMessageCodeToProcessCode(data.messageCode).toString} MessageId=${data.messageId.getOrElse("")}]"
      val to = data.receiver.toUpperCase()
      val tenant = data.sender.toUpperCase()

      logger.debug(s"Prepare Email Message Flow: ${data}")
      EmailService.EmailModel(tenant = tenant, toEmail = to,
        subject = subject, attachment = attachment, data = data)
    })

  def run( source: Source[MqttMessage, Future[Done]],
           responseSink: Sink[MqttMessage, _],
           errorSink: Sink[MqttMessage, _],
         ): Future[Done] = {

    implicit val timeout: Timeout = Timeout(15.seconds)
    val invalidEventsCommitter: Sink[Either[MqttMessage, _], NotUsed] =
      Flow[Either[MqttMessage, _]]
        .collect { case Left(error) => error }
        .to(errorSink)

    source
      .map(msg => (msg.payload.utf8String))
//      .map(m => {
//        println("####### ", m)
//        m
//      })
      .via(decodingFlow.divertTo(invalidEventsCommitter, _.isLeft).collect { case Right(m) => m })
      .via(prepareMessageFlow)
      .via(prepareEmailMessageFlow)
      .via(
        ActorFlow.ask(parallelism = 1)(mailService)((msg: EmailService.EmailModel, replyTo: ActorRef[EmailCommand]) =>
          DistributeMail(msg.tenant, msg, replyTo)).collect {
          case EmailService.SendEmailResponse(msg) => Right(msg)
          case err : EmailService.SendErrorResponse => {
            println(s"Receive Errormessage sending Mail ${err}")
            Left(err)
          }
        }
      )
      .flatMapConcat {
        case Left(error) ⇒ Source
          .single(error)
          .via(Flow.fromFunction( err =>
            MqttMessage(s"eda/response/${err.tenant}/error", ByteString(err.asJson.toString().strip())))
          )
        case Right(msg) => Source
          .single(msg)
          .via(storeMessageFlow)
      }
      .to(responseSink)
      .run()
  }
}


object MqttRequestStream {
  def apply(mailService: ActorRef[EmailCommand],
            messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
            messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
           (implicit system: ActorSystem[_]):MqttRequestStream = {
    new MqttRequestStream(mailService, messageTransformer, messageStore)
  }
}
