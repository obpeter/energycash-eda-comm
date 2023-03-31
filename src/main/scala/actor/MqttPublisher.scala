package at.energydash
package actor

import domain.email.Fetcher.MailMessage

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import at.energydash.actor.FetchMailActor.DeleteEmailCommand
import at.energydash.actor.commands.EmailCommand
import at.energydash.config.Config
import at.energydash.domain.eda.message.{CMRequestRegistrationOnlineMessage, CPRequestZPListMessage, ConsumptionRecordMessage, EdaErrorMessage, EdaMessage}
import at.energydash.model.EbMsMessage
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContextExecutor, Future}

object MqttPublisher {
  import at.energydash.model.JsonImplicit._
  trait MqttCommand
  case class MqttPublish(tenant: String, mails: List[EdaMessage[_]]/*, mailProvider: ActorRef[EmailCommand]*/) extends MqttCommand
  case class MqttPublishError(tenant: String, message: String) extends MqttCommand

  def apply(): Behavior[MqttCommand] =
    Behaviors.setup { implicit ctx =>
      import ctx.system
      implicit val ex: ExecutionContextExecutor = ctx.system.executionContext
      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-publisher-${1}")

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestZPListMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestRegistrationOnlineMessage => MqttMessage(s"${Config.cmTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessage => MqttMessage(s"${Config.energyTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: EdaErrorMessage => MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[MqttCommand] =
        Behaviors.receiveMessage {
          case MqttPublish(tenant, response) =>
            Source(response)
              .map(x => convertCPRequestMessageToJson(x))
              .to(responseSink)
              .run()
            Behaviors.same
          case MqttPublishError(tenantId, msg) =>
            ctx.log.info(s"Error ${tenantId} ${msg}")
            Behaviors.same
        }

      process()
    }

  private def createResponseSink(customerId: String): Sink[MqttMessage, Future[Done]] = {
    MqttSink(MqttConnectionSettings(
      Config.getMqttMailConfig.url,
      customerId,
      new MemoryPersistence)
      .withAutomaticReconnect(true)
    , MqttQoS.AtLeastOnce)
  }
}
