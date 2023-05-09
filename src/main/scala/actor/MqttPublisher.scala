package at.energydash
package actor

import config.Config
import domain.eda.message._

import akka.Done
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContextExecutor, Future}

object MqttPublisher extends StrictLogging{
  import model.JsonImplicit._
  trait MqttCommand
  case class MqttPublish(tenant: String, mails: List[EdaMessage[_]]/*, mailProvider: ActorRef[EmailCommand]*/) extends MqttCommand
  case class MqttPublishError(tenant: String, message: String) extends MqttCommand

  def apply(): Behavior[MqttCommand] =
    Behaviors.setup { implicit ctx =>
      import ctx.system
      implicit val ex: ExecutionContextExecutor = ctx.system.executionContext
      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-publisher-${1}")

      ctx.log.info(s"MQTT Publish started. Configuration -> ${Config.getMqttMailConfig}")

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestZPListMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestRegistrationOnlineMessage => MqttMessage(s"${Config.cmTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessage => MqttMessage(s"${Config.energyTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces)).withQos(MqttQoS.atMostOnce)
        case x: EdaErrorMessage => MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[MqttCommand] =
        Behaviors.receiveMessage {
          case MqttPublish(tenant, response) =>
            Source(response)//.throttle(12, 1.minute)
//              .log("EDA_CODE", res => logger.info(res.toString))
              .map(x => convertCPRequestMessageToJson(x))
              .log("mqtt", x => {
                logger.info(s"Send MQTT Message to ${x.topic}")
              })
              .runWith(responseSink)
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
