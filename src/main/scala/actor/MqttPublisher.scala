package at.energydash
package actor

import config.Config
import domain.eda.message._
import mqtt.CommandMessage
import mqtt.MqttProtocol._

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.alpakka.mqtt.MqttMessage
import akka.stream.alpakka.mqtt.streaming._
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._

object MqttPublisher extends StrictLogging{
  import model.JsonImplicit._

  trait MqttCommand
  case class EdaNotification(protocol: String, message: EdaMessage[_])
  case class MqttPublish(mails: List[EdaNotification]/*, mailProvider: ActorRef[EmailCommand]*/) extends MqttCommand
  case class MqttPublishCommand(command: CommandMessage) extends MqttCommand
  case class MqttPublishError(tenant: String, message: String) extends MqttCommand

  def apply(mqttSystem: ActorRef[MqttCmd]): Behavior[MqttCommand] = {

    Behaviors.setup { implicit ctx =>
      val mqttMessage = (topic: String, msg: ByteString) => MqttMessage(topic, msg) //.withQos(MqttQoS.AtLeastOnce).withRetained(true)

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestZPListMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: CPRequestMeteringValueMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestRegistrationOnlineMessageV0100 => MqttMessage(s"${Config.cmTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessageV0130 => mqttMessage(s"${Config.energyTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: EdaErrorMessage => MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case _ =>
          logger.info("Not able to handle Message")
          MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[MqttCommand] =
        Behaviors.receiveMessage {
          case MqttPublish(notification) =>
            notification.foreach(x => mqttSystem ! EdaEventReceived(EdaInboundMessage(x.protocol, x.message.message), None))
            Behaviors.same
          case MqttPublishCommand(command) =>
            mqttSystem ! EdaMessageCommand(command, None)
            Behaviors.same
          case MqttPublishError(tenantId, msg) =>
            ctx.log.info(s"Error ${tenantId} ${msg}")
            Behaviors.same
        }

      process()
    }
  }
}
