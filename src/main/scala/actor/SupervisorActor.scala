package at.energydash
package actor

import actor.commands._
import actor.TenantProvider.TenantStart
import config.Config
import domain.mqtt.MqttEmail
import domain.stream.MqttRequestStream

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

object SupervisorActor {

  def apply(): Behavior[Command] =
    Behaviors.setup { implicit ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val timeout: Timeout = Timeout(5.seconds)
      implicit val ex: ExecutionContextExecutor = system.executionContext

      val messageTransformer = ctx.spawn(PrepareMessageActor(), "message-transformer")
      val messageStore = ctx.spawn(MessageStorage(), name = "message-storage")
      val mqttPublisher = ctx.spawn(MqttPublisher(), name = "mqtt-publisher")
      val tenantProvider = ctx.spawn(TenantProvider(mqttPublisher, messageStore), name = "tenant-provider")

      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-mail")
      val mqttRequestStream = MqttRequestStream(tenantProvider, messageTransformer, messageStore)

      def process(): Behavior[Command] =
        Behaviors.receiveMessage {
          case Start =>
            tenantProvider ! TenantStart
            mqttRequestStream.run(
                MqttEmail.mqttSource,
                createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response"),
                createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-error"))
            Behaviors.same
          case Shutdown =>
//            ctx.stop()
            Behaviors.same
        }
      process()
    }

  private def createResponseSink(customerId: String): Sink[MqttMessage, Future[Done]] = {
    val customResponseSettings = MqttConnectionSettings(
      Config.getMqttMailConfig.url,
      customerId,
      new MemoryPersistence)
      .withAutomaticReconnect(true)

    MqttSink(customResponseSettings, MqttQoS.AtLeastOnce)
  }
}
