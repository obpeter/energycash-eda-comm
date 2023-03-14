package at.energydash
package actor

import domain.email.EmailService.{EmailModel, SendEmailCommand, SendErrorResponse}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import at.energydash.actor.FetchMailActor.DeleteEmailCommand
import at.energydash.actor.commands.{Command, EmailCommand}
import at.energydash.config.Config
import org.slf4j.LoggerFactory

class TenantProvider(supervisor: ActorRef[Command], messageStore: ActorRef[MessageStorage.Command[_]]) {

  import TenantProvider._
  var logger = LoggerFactory.getLogger(classOf[TenantProvider])

  def start: Behavior[EmailCommand] = Behaviors.setup[EmailCommand] { context => {
    def setup(): Behavior[EmailCommand] = {
      Behaviors.receiveMessage {
        case Start =>
          val tenants = Config.getTenants
          val a = tenants.map(t => (t -> context.spawn(FetchMailTenantWorker(t, supervisor, messageStore), s"worker-$t"))).toMap
          provide(a)
      }
    }

    def provide(tenantActors: Map[String, ActorRef[EmailCommand]]): Behavior[EmailCommand] = {
      logger.debug(s"Listen for mqtt messages ${tenantActors.map(t => t.toString())}")
      Behaviors.receiveMessage {
        case DistributeMail(tenant, mail, replyTo) =>
          context.log.debug(s"Distribute Mail from ${tenant} to ${mail.toEmail}")
          tenantActors.get(tenant.toUpperCase()) match {
            case Some(a) => a ! SendEmailCommand(mail, replyTo)
            case None => replyTo ! SendErrorResponse(tenant, "Tenant not registered")
          }
          Behaviors.same
        case DeleteMail(tenant, messageId) =>
          tenantActors.get(tenant) match {
            case Some(a) => a ! DeleteEmailCommand(tenant, messageId)
            case None =>
          }
          Behaviors.same
      }
    }
    setup
  }}
}

object TenantProvider {

  case object Start extends EmailCommand

  case class DistributeMail(tenant: String, mail: EmailModel, replyTo: ActorRef[EmailCommand]) extends EmailCommand

  case class DeleteMail(tenant: String, messageId: String) extends EmailCommand

  def apply(supervisor: ActorRef[Command], messageStore: ActorRef[MessageStorage.Command[_]]): Behavior[EmailCommand] =
    new TenantProvider(supervisor, messageStore).start
}