package at.energydash
package actor

import config.Config
import domain.eda.message._
import mqtt.MqttProtocol._

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, Mqtt}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete, Tcp}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContextExecutor, Future}

object MqttPublisher extends StrictLogging{
  import model.JsonImplicit._
  trait MqttCommand
  case class MqttPublish(tenant: String, mails: List[EdaMessage[_]]/*, mailProvider: ActorRef[EmailCommand]*/) extends MqttCommand
  case class MqttPublishError(tenant: String, message: String) extends MqttCommand

  def apply(mqttSystem: ActorRef[MqttCmd]): Behavior[MqttCommand] = {

    Behaviors.setup { implicit ctx =>
      import ctx.system
      implicit val ex: ExecutionContextExecutor = ctx.system.executionContext
      val csc = ctx.system.classicSystem
//      implicit val a = ctx.system
      //      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-publisher-${1}")

      val settings =  MqttSessionSettings()
      val session = ActorMqttClientSession(settings)(csc)

      val connection = Tcp()(csc).outgoingConnection("localhost", 1883)

      val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
        Mqtt
          .clientSessionFlow(session, ByteString("1"))
          .join(connection)


      val (commands: SourceQueueWithComplete[Command[Nothing]], events: Future[Publish]) =
        Source
          .queue(10, OverflowStrategy.fail)
          .via(mqttFlow)
          .collect {
            case Right(Event(p: Publish, _)) => p
          }
          .toMat(Sink.head)(Keep.both)
          .run()

      commands.offer(Command(Connect("edash-clientId-100", ConnectFlags.CleanSession)))

      ctx.log.info(s"MQTT Publish started. Configuration -> ${Config.getMqttMailConfig}")

      val sink = MqttSink(mqttConnectionOptions.withClientId("publish-edash-messages-1"), MqttQoS.AtMostOnce)

      val mqttMessage = (topic: String, msg: ByteString) => MqttMessage(topic, msg) //.withQos(MqttQoS.AtLeastOnce).withRetained(true)

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestZPListMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: CPRequestMeteringValueMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestRegistrationOnlineMessage => MqttMessage(s"${Config.cmTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessage => mqttMessage(s"${Config.energyTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case x: EdaErrorMessage => MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
        case _ =>
          logger.info("Not able to handle Message")
          MqttMessage(s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[MqttCommand] =
        Behaviors.receiveMessage {
          case MqttPublish(tenant, response) =>

            response.foreach(x => mqttSystem ! EdaEventReceived(EdaInboundMessage("CRMSG", x.message), None))

//            response.foreach(x => {
//              println(s"Send Message ${tenant}")
//              session ! Command(
//                Publish(ControlPacketFlags.RETAIN | ControlPacketFlags.QoSAtLeastOnceDelivery,
//                  s"${Config.errorTopic}/${x.message.receiver.toLowerCase}", ByteString(x.message.asJson.deepDropNullValues.noSpaces)))
//
//            }
//            )



//            Source(response) //.throttle(12, 1.minute)
//              .map(convertCPRequestMessageToJson)
//              .log("mqtt", x => {
//                logger.info(s"Send MQTT Message to ${x.topic}")
//              })
//              .runWith(sink)
            Behaviors.same
          case MqttPublishError(tenantId, msg) =>
            ctx.log.info(s"Error ${tenantId} ${msg}")
            Behaviors.same
        }

      process()
    }
  }
//  private def createResponseSink(customerId: String): Sink[MqttMessage, Future[Done]] = {
////    MqttSink(MqttConnectionSettings(
////      Config.getMqttMailConfig.url,
////      customerId,
////      new MemoryPersistence)
////      .withAutomaticReconnect(true)
////      .withCleanSession(false)
////    , MqttQoS.AtLeastOnce)
//    MqttFlow
//      .atLeastOnceWithAck(MqttConnectionSettings(
//            Config.getMqttMailConfig.url,
//            customerId,
//            new MemoryPersistence).withAutomaticReconnect(true).withCleanSession(false), MqttSubscriptions.empty, 0, MqttQoS.AtLeastOnce)
//      .toMat(Sink.ignore)(Keep.right)
//  }

  private def mqttConnectionOptions = MqttConnectionSettings(
        Config.getMqttMailConfig.url,
        "edash-producer-id",
        new MemoryPersistence)
    .withAutomaticReconnect(true)
    .withMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1)
    .withMaxInFlight(20)
    .withCleanSession(true)
}
