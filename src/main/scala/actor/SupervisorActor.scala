package at.energydash
package actor

import actor.FetchMailActor.DeleteEmailCommand
import actor.commands._
import actor.TenantProvider.{DeleteMail, Start => TenantStart}
import config.Config
import domain.eda.message.{CMRequestProcessMessage, CPRequestZPListMessage, ConsumptionRecordMessage, EdaMessage}
import domain.email.EmailService
import domain.mqtt.MqttEmail
import domain.stream.MqttRequestStream

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.stream.alpakka.mqtt.scaladsl.MqttSink
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import at.energydash.domain.email.EmailService.FetchEmailResponse
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

object SupervisorActor {

//  sealed trait Command
//  final object Start extends Command
//  case class Send(data: SendData, replyTo: ActorRef[Response]) extends Command

  import at.energydash.model.JsonImplicit._

  def apply(): Behavior[Command] =
    Behaviors.setup { implicit ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val timeout: Timeout = Timeout(5.seconds)
      implicit val ex: ExecutionContextExecutor = system.executionContext

//      val emailService = ctx.spawn(EmailService(), "email-service")
      val messageTransformer = ctx.spawn(PrepareMessageActor(), "message-transformer")
      val messageStore = ctx.spawn(MessageStorage(), name = "message-storage")
      val tenantProvider = ctx.spawn(TenantProvider(ctx.self, messageStore), name = "tenant-provider")

//      val supervisedMailManager = Behaviors.supervise(FetchMailManager(ctx.self, messageStore)).onFailure[Exception](SupervisorStrategy.restart)
//      val mailManager = ctx.spawn(supervisedMailManager, name = "mail-manager")

//      val supervisedMailActor = Behaviors.supervise(FetchMailActor(messageStore)).onFailure[Exception](SupervisorStrategy.restart)
//      val mailActor = ctx.spawn(supervisedMailActor, name = "mail-actor")

//      Config.getTenants.foreach(t => ctx.spawn(FetchMailTenantWorker(t, ctx.self, messageStore.ref), s"mailworker-$t"))

      val responseSink = createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-mail")
      val mqttRequestStream = MqttRequestStream(tenantProvider, messageTransformer, messageStore)

//      def deleteMailFlow(message: Fetcher.MailMessage): Future[Fetcher.MailMessage] = {
//        mailManager.tell(DeleteEmailCommand(message.messageId))
//        Future {
//          message
//        }
//      }

      def convertCPRequestMessageToJson(x: EdaMessage[_]): MqttMessage = x match {
        case x: CPRequestZPListMessage => MqttMessage(s"${Config.cpTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: CMRequestProcessMessage => MqttMessage(s"${Config.cmTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
        case x: ConsumptionRecordMessage => MqttMessage(s"${Config.energyTopic}/${x.message.receiver.toLowerCase}", ByteString(x.asJson.deepDropNullValues.noSpaces))
      }

      def process(): Behavior[Command] =
        Behaviors.receiveMessage {
          case Start =>
            tenantProvider ! TenantStart
            mqttRequestStream.run(
                MqttEmail.mqttSource,
                createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response"),
                createResponseSink(s"${Config.getMqttMailConfig.consumerId}-response-error"))
            Behaviors.same
          case FetchEmailResponse(tenant, response) =>
            Source(response)
              .mapAsync(1) { x =>
                tenantProvider.tell(DeleteMail(tenant, x.messageId))
                Future(x)
              }
              .map(x => convertCPRequestMessageToJson(x.content))
              .to(responseSink)
              .run()
            Behaviors.same
          case ErrorResponse(msg) =>
            ctx.log.info(s"Error ${msg}")
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
