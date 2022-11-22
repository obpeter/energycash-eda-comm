package at.energydash
package actor

import domain.email.EmailService

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import at.energydash.actor.commands._
import at.energydash.domain.eda.message.{CMRequestProcessMessage, CPRequestMessage, ConsumptionRecordMessage, EdaMessage}
import at.energydash.domain.mqtt.MqttEmail
import at.energydash.domain.stream.MqttRequestStream
import at.energydash.domain.util.Config
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object SupervisorActor {

//  sealed trait Command
//  final object Start extends Command
//  case class Send(data: SendData, replyTo: ActorRef[Response]) extends Command

  import at.energydash.model.JsonImplicit._

  def apply(): Behavior[Command] =
    Behaviors.setup { implicit ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val timeout: Timeout = Timeout(5.seconds)

      val emailService = ctx.spawn(EmailService(), "email-service")
      val messageTransformer = ctx.spawn(PrepareMessageActor(), "message-transformer")
      val messageStore = ctx.spawn(MessageStorage(), name = "message-storage")

      val supervisedMailManager = Behaviors.supervise(FetchMailManager(ctx.self, messageStore)).onFailure[Exception](SupervisorStrategy.restart)
      val mailManager = ctx.spawn(supervisedMailManager, name = "mail-manager")

      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-mail")
      val mqttRequestStream = MqttRequestStream(emailService, messageTransformer, messageStore)

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestMessage => MqttMessage(s"eda/response/${x.message.receiver.toLowerCase}/cpresponse", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestProcessMessage => MqttMessage(s"eda/response/${x.message.receiver.toLowerCase}/cmresponse", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessage => MqttMessage(s"eda/response/energy/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[Command] =
        Behaviors.receiveMessage {
          case Start =>
            mqttRequestStream.run(
              MqttEmail.mqttSource,
              createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response"),
              createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-error"))
            Behaviors.same
          case FetchEmailResponse(response) =>
            Source(response)
              .map(x => convertCPRequestMessageToJson(x.content))
              .to(responseSink)
              .run()
            Behaviors.same
        }

      process()
    }

  def createResponseSink(customerId: String): Sink[MqttMessage, Future[Done]] = {
    val customResponseSettings = MqttConnectionSettings(
      Config.getMqttMailConfig.url,
      customerId,
      new MemoryPersistence)
      .withAutomaticReconnect(true)

    MqttSink(customResponseSettings, MqttQoS.AtLeastOnce)
  }
}
