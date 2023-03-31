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
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import at.energydash.actor.SupervisorActor.startHttpServer
import at.energydash.actor.routes.ServiceRoute
import at.energydash.services.FileService
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object SupervisorActor {
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8880).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

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

            val fileService = FileService(system, mqttPublisher)
            val routes = new ServiceRoute(fileService)
            startHttpServer(routes.adminRoutes)

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
