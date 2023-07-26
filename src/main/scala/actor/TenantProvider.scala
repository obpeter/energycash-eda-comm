package at.energydash
package actor

import actor.MqttPublisher.MqttCommand
import actor.TenantMailActor.DeleteEmailCommand
import actor.commands.EmailCommand
import domain.dao.model.TenantConfig
import domain.dao.spec.{Db, SlickEmailOutboxRepository, SlickTenantConfigRepository}
import domain.email.EmailService.{EmailModel, EmitSendEmailCommand, SendErrorResponse}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Success, Failure}

class TenantProvider(supervisor: ActorRef[MqttCommand], messageStore: ActorRef[MessageStorage.Command[_]]) {

  import TenantProvider._
  var logger = LoggerFactory.getLogger(classOf[TenantProvider])

  def start: Behavior[EmailCommand] = Behaviors.setup[EmailCommand] { context => {
    import context.executionContext

    val dbConfig = Db.getConfig
    val mailRepo = new SlickEmailOutboxRepository(dbConfig)
    val tenantConfigRepository = new SlickTenantConfigRepository(dbConfig)
    //    tenantConfigRepository.init()

    def setup(): Behavior[EmailCommand] = {
      Behaviors.receiveMessage {
        case TenantStart =>
          val tenants = Await.result(tenantConfigRepository.allActivated(), 3.seconds)
          val a = tenants.map(t => (t.tenant -> context.spawn(FetchMailTenantWorker(t, supervisor, messageStore, mailRepo), s"worker-${t.tenant}"))).toMap
          provide(a)
//          tenantConfigRepository.allActivated().collect {
//            case tenants  => {
//              val a = tenants.map(t => (t.tenant -> context.spawn(FetchMailTenantWorker(t, supervisor, messageStore, mailRepo), s"worker-${t.tenant}"))).toMap
//              provide(a)
//            }
//          }
//          Behaviors.same
      }
    }

    def provide(tenantActors: Map[String, ActorRef[EmailCommand]]): Behavior[EmailCommand] = {
      logger.debug(s"Listen for mqtt messages ${tenantActors.map(t => t.toString())}")
      Behaviors.receiveMessage {
        case DistributeMail(tenant, mail, replyTo) =>
          context.log.debug(s"Distribute Mail from ${tenant} to ${mail.toEmail}")
          tenantActors.get(tenant.toUpperCase()) match {
            case Some(a) => a ! EmitSendEmailCommand(mail, replyTo)
            case None => replyTo ! SendErrorResponse(tenant, "Tenant not registered")
          }
          Behaviors.same
        case DeleteMail(tenant, messageId) =>
          tenantActors.get(tenant) match {
            case Some(a) => a ! DeleteEmailCommand(tenant, messageId)
            case None =>
          }
          Behaviors.same
        case AddTenant(tenantConfig, replyTo) =>
          tenantConfigRepository.create(tenantConfig).onComplete {
            case Success(_) =>
              context.self ! TenantAdded(tenantConfig, replyTo)
            case Failure(e) =>
              replyTo ! ResponseError(e.getMessage)
          }
          Behaviors.same

        case TenantAdded(tenantConfig, replyTo) =>
          replyTo ! ResponseOk
          provide(
            tenantActors +
              (tenantConfig.tenant -> context.spawn(FetchMailTenantWorker(tenantConfig, supervisor, messageStore, mailRepo), s"worker-${tenantConfig.tenant}")))

      }
    }

    setup()
  }}
}

object TenantProvider {

  case object TenantStart extends EmailCommand

  case class DistributeMail(tenant: String, mail: EmailModel, replyTo: ActorRef[EmailCommand]) extends EmailCommand

  case class DeleteMail(tenant: String, messageId: String) extends EmailCommand

  case class AddTenant(tenant: TenantConfig, replyTo: ActorRef[EmailCommand]) extends EmailCommand

  case class TenantAdded(tenant: TenantConfig, replyTo: ActorRef[EmailCommand]) extends EmailCommand

  case class ResponseError(msg: String) extends EmailCommand

  case object ResponseOk extends EmailCommand

  def apply(mqttPublisher: ActorRef[MqttCommand], messageStore: ActorRef[MessageStorage.Command[_]]): Behavior[EmailCommand] =
    new TenantProvider(mqttPublisher, messageStore).start
}