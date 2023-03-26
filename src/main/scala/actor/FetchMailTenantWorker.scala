package at.energydash
package actor

import actor.FetchMailActor.{DeleteEmailCommand, FetchEmailCommand}
import actor.commands.{Command, EmailCommand}

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import at.energydash.actor.MqttPublisher.MqttCommand
import at.energydash.domain.dao.model.TenantConfig
import at.energydash.domain.dao.spec.{Db, SlickEmailOutboxRepository}
import at.energydash.domain.email.EmailService.SendEmailCommand

import scala.concurrent.duration.DurationInt

class FetchMailTenantWorker(timers: TimerScheduler[EmailCommand],
                            tenant: TenantConfig,
                            mqttPublisher: ActorRef[MqttCommand],
                            messageStore: ActorRef[MessageStorage.Command[_]],
                            mailRepo: SlickEmailOutboxRepository) {
  import FetchMailTenantWorker._

  val rand = new scala.util.Random

  private def setup(): Behavior[EmailCommand] = {
    Behaviors.setup { context => {
      context.log.info("Setup Tenant Worker")

      timers.startTimerWithFixedDelay(TimerKey, Refresh, 1.minute/* + (rand.nextInt(60*15)).seconds*/)
      val mailActor = context.spawn(FetchMailActor(tenant, messageStore, mailRepo), name = "mail-actor")

      def activated(mailActor: ActorRef[EmailCommand]): Behavior[EmailCommand] = {
        println("Activate Tenant Worker")
        Behaviors.receiveMessage[EmailCommand] {
          case Refresh =>
            mailActor ! FetchEmailCommand(tenant.tenant, "", mqttPublisher)
            Behaviors.same

          case WaitResponse =>
            mailActor ! FetchEmailCommand(tenant.tenant, "", mqttPublisher)
            Behaviors.same

          case msg@SendEmailCommand(_, _) =>
            context.log.debug(s"Forward mail to Mail Actor ${msg.email.toEmail}")

            mailActor ! msg
            timers.startSingleTimer(Refresh, 1.minute)

            Behaviors.same

          case msg: DeleteEmailCommand =>
            mailActor ! msg
            Behaviors.same
        }
      }

      activated(mailActor)
    }}
  }
}

object FetchMailTenantWorker {

  private case object Refresh extends EmailCommand
  private case object WaitResponse extends EmailCommand
  private case object TimerKey

  def apply(tenantConfig: TenantConfig,
            mqttPublisher: ActorRef[MqttCommand],
            messageStore: ActorRef[MessageStorage.Command[_]],
            mailRepo: SlickEmailOutboxRepository): Behavior[EmailCommand] = {
    Behaviors.withTimers(timers => new FetchMailTenantWorker(timers, tenantConfig, mqttPublisher, messageStore, mailRepo).setup())
  }
}