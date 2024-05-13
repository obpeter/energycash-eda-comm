package at.energydash
package actor

import actor.TenantProvider.TenantStart
import actor.routes.{FileService, ServiceRoute}
import config.Config
import domain.stream.MqttRequestStream
import mqtt.MqttSystem

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.Sink
import akka.util.Timeout
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

  private def startGRPCServer(tenantProvider: ActorRef[EmailCommand])(implicit system: ActorSystem[_]) = {
    AdminServer.apply(tenantProvider, system)
  }

  def apply(): Behavior[Command] =
    Behaviors.setup { implicit ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val timeout: Timeout = Timeout(5.seconds)
      implicit val ex: ExecutionContextExecutor = system.executionContext

      val mqttSystem = ctx.spawn(MqttSystem(config.Config.getMqttConfig()), name = "mqtt-system")

      val messageTransformer = ctx.spawn(PrepareMessageActor(), "message-transformer")
      val messageStore = ctx.spawn(MessageStorage(), name = "message-storage")
      val mqttPublisher = ctx.spawn(MqttPublisher(mqttSystem), name = "mqtt-publisher")
      val tenantProvider = ctx.spawn(TenantProvider(mqttPublisher, messageStore), name = "tenant-provider")

//      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-mail")
      val mqttRequestStream = MqttRequestStream(tenantProvider, messageTransformer, messageStore)


      def process(): Behavior[Command] =
        Behaviors.receiveMessage {
          case Start =>
            tenantProvider ! TenantStart
            mqttRequestStream.startCommand()
            val fileService = FileService(system, mqttPublisher)
            val routes = new ServiceRoute(fileService, messageStore)
            startHttpServer(routes.adminRoutes)
            startGRPCServer(tenantProvider)

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
