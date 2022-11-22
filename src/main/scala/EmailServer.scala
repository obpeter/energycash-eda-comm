package at.energydash

import domain.email.EmailService
import domain.email.EmailService.{ErrorResponse, Response}
import domain.mqtt
import domain.mqtt.{MqttEDAEnvelop, MqttEmail}
import domain.util.Config

import akka.{Done, NotUsed}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.event.Logging
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.typed.scaladsl.ActorFlow
import akka.stream.{Attributes, Materializer, SystemMaterializer}
import akka.util.{ByteString, Timeout}
import at.energydash.domain.eda.message.{CPRequestMessage, MessageHelper}
import at.energydash.model.EbMsMessage
import scalaxb.Helper
import xmlprotocol.{ANFORDERUNG_AP, GCRequestAP, GCRequestAP_EXT, MarketParticipantDirectoryType, Number01Value2, ProcessDirectoryType, RoutingAddress, RoutingHeader}

import java.io.StringWriter
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.xml.XML
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.Error
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import java.util.{Date, GregorianCalendar}

object EmailServer {

  case class SendData(from: String, to: String, ebMsMessage: EbMsMessage)

  sealed trait Message
  final private case class StartFailed(cause: Throwable) extends Message

  //  final private case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message
  case class Send(data: SendData, replyTo: ActorRef[Response]) extends Message


  def apply(host: String, port: Int): Behavior[Message] =
    Behaviors.setup { ctx =>
      implicit val system = ctx.system

      implicit val timeout: Timeout = 5.seconds
      implicit val ec: ExecutionContextExecutor = ctx.executionContext

      val emailService = ctx.spawn(EmailService(), "email-service")

      //      val route = new EmailRoutes(emailService)
      //
      //      val serverBinding = Http().newServerAt(host, port).bind(route.emailServiceRoutes)

      //      ctx.pipeToSelf(serverBinding) {
      //        case Success(binding) =>
      //          Started(binding)
      //        case Failure(ex) =>
      //          StartFailed(ex)
      //      }

      //      def running(binding: ServerBinding): Behavior[Message] =
      def running(): Behavior[Message] =
        Behaviors
          .receiveMessagePartial[Message] {
            case Stop =>
              ctx.log.info(
                "Stopping server "
              )
              Behaviors.stopped
            case Send(data, replyTo) => {

              val attachment = MessageHelper.getEdaMessageByType(data.ebMsMessage).toByte
              val subject = s"[${data.ebMsMessage.messageCode} MessageId=${data.ebMsMessage.messageId}]"

              emailService.tell(
//              emailService.ask((replyTo: ActorRef[Response]) =>
//                EmailService.SendEmailCommand(EmailService.EmailModel(toEmail = "obermueller.peter@gmail.com", subject = "[12345678 MessageId=0987654321]", body = "Hallo"), replyTo)
                EmailService.SendEmailCommand(EmailService.EmailModel(toEmail = data.to,
                  subject = subject, attachment = attachment, data = data.ebMsMessage), replyTo)
              )
              Behaviors.same
            }
          }
          .receiveSignal { case (_, PostStop) =>
            //            binding.unbind()
            Behaviors.same
          }

      //      def sending(): Behaviors.Receive[Response] =
      //        Behaviors.receiveMessage[Response] {
      //          case SendEmailResponse(email) => {
      //            println("Email successfully send")
      //            running()
      //          }
      //          case ErrorResponse(message) => {
      //            println("Error sending email: {}", message)
      //            running()
      //          }
      //          case _ =>
      //            Behaviors.same
      //        }

      def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
        Behaviors.receiveMessage[Message] {
          case StartFailed(cause) =>
            throw new RuntimeException("Server failed to start", cause)
            //          case Started(binding) =>
            //            ctx.log.info(
            //              "Server online at http://{}:{}",
            //              binding.localAddress.getHostString,
            //              binding.localAddress.getPort
            //            )

            if (wasStopped)
              ctx.self ! Stop
            //            running(binding)

            running()

          case Stop =>
            starting(wasStopped = true)
        }



      //      starting(wasStopped = false)
      running()
    }

  def main(args: Array[String]): Unit = {
    var actorMain = ActorSystem(EmailServer.apply(Config.host, Config.port), "EpmsEmailServer")
//    email ! Send

    implicit val materializer: Materializer = SystemMaterializer(actorMain).materializer
    implicit val askTimeout: Timeout = 5.seconds

//    import mqtt.ResponseJsonSupport._
//    import spray.json._

    import at.energydash.model.JsonImplicit._

//    val msg = """{"msgType":"SENDEN_VDC", "messageId":"rctest202210161905235386216409991", "conversationId":"ectest202210161905235380027488851", "sender":"rctest", "receiver":"ectest", "payload":""}"""

    println("....")

    val xmlObj = GCRequestAP(
      MarketParticipantDirectoryType(
        RoutingHeader(
          RoutingAddress("AT003000"),
          RoutingAddress("AT003000"),
          Helper.toCalendar("2002-10-10T12:00:00-05:00")
        ),
        Number01Value2,
        ANFORDERUNG_AP,
      ),
      ProcessDirectoryType(
        "MessageId",
        "ConversationId",
        Helper.toCalendar("2002-10-10T12:00:00-05:00"),
        "MeteringPoint",
        GCRequestAP_EXT("ParticipantMeter", Some(BigDecimal(0.0))) :: Nil
      )
    )

    val xmlObj1 = scalaxb.toXML[GCRequestAP](xmlObj, Some("http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00"), Some("GCRequestAP"),
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("cp1") -> "http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true)

    val xmlString = new StringWriter()
    XML.write(xmlString, xmlObj1.head, "UTF-8", true, null)

    println(s"xml with qualified child: $xmlString")

    println("----")
    println(new GregorianCalendar)

    val jsonObject = """{"messageCode":"SENDEN_VDC", "messageId":"rctest202210161905235386216409991", "conversationId":"ectest202210161905235380027488852", "sender":"rctest", "receiver":"ectest"}"""
    val myObj = decode[EbMsMessage](jsonObject)
    println(myObj)
    val xmlObj2 = myObj match {
      case Right(m) => CPRequestMessage(m)
    }
    println(xmlObj2.toXML)
    println("----")

    println("....")

    val consumerResponseSettings = MqttConnectionSettings(
      Config.getMqttMailConfig.url,
      Config.getMqttMailConfig.consumerId + "response",
      new MemoryPersistence)
      .withAutomaticReconnect(true)


    import at.energydash.model.FlowOps._

    val mailMqtt = MqttEmail.mqttSource
    val responseSink: Sink[MqttMessage, Future[Done]] =
      MqttSink(consumerResponseSettings, MqttQoS.AtLeastOnce)

    val invalidEventsCommitter: Sink[Either[MqttMessage, _], NotUsed] =
      Flow[Either[MqttMessage, _]]
        .collect { case Left(offset) => offset }
        .to(responseSink)

    val decodingFlow:Flow[String, Either[MqttMessage, EbMsMessage], NotUsed] =
      Flow.fromFunction(msg => decode[EbMsMessage](msg).left.map(error =>
        MqttMessage("response", ByteString(ErrorResponse(error.getMessage).asJson.toString().strip()))
      ))

    val decodingFlowDiverted: Flow[String, EbMsMessage, NotUsed] =
      decodingFlow.divertLeft(to = Sink.ignore.mapMaterializedValue(_ => NotUsed))
//      decodingFlow.divertTo(responseSink.mapMaterializedValue(_), _.isLeft).collect {case Right(m) => m}

    mailMqtt
      .map(msg => (msg.payload.utf8String))
//      .map(msg => decode[EbMsMessage](msg))
//      .log(name = "myStream")
//      .withAttributes(
//        Attributes.logLevels(
//          onElement = Logging.InfoLevel,
//          onFinish = Logging.InfoLevel,
//          onFailure = Logging.DebugLevel
//        )
//      )
//      .collect {
//        case Right(msg) => SendData("sepp.gaug@gmail.com", "obermueller.peter@gmail.com", msg)
//      }
      .via(decodingFlow.divertTo(invalidEventsCommitter, _.isLeft).collect {case Right(m) => m})
      .map(s => SendData("sepp.gaug@gmail.com", "obermueller.peter@gmail.com", s))
      .via(
        ActorFlow.ask(parallelism = 1)(actorMain)((msg: SendData, replyTo: ActorRef[Response]) =>
          Send(msg, replyTo))
      )
      .map(msg => MqttMessage("response", ByteString(msg.asJson.toString())))
      .to(responseSink)
      .run()
//      .runForeach(println)
  }

}
