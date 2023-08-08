package at.energydash
package actor

import actor.MqttPublisher.MqttCommand
import actor.TenantMailActor.{DeleteEmailCommand, FetchEmailCommand}
import actor.commands.EmailCommand
import config.Config
import domain.dao.SlickEmailOutboxRepository
import domain.email.EmailService.{EmitSendEmailCommand, SendEmailCommand}
import model.dao.TenantConfig

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration, MILLISECONDS}

class FetchMailTenantWorker(timers: TimerScheduler[EmailCommand],
                            tenant: TenantConfig,
                            mqttPublisher: ActorRef[MqttCommand],
                            messageStore: ActorRef[MessageStorage.Command[_]],
                            mailRepo: SlickEmailOutboxRepository) {
  import FetchMailTenantWorker._
  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration   =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  val rand = new scala.util.Random
  val interval: FiniteDuration = Config.interval(tenant.domain)
  println(s"Interval: ${interval.toMillis}")

  private def setup(): Behavior[EmailCommand] = {
    Behaviors.setup { context => {
      context.log.info("Setup Tenant Worker")

      timers.startTimerWithFixedDelay(TimerKey, Refresh, interval + Duration(rand.nextLong(interval.toMillis) / 2, MILLISECONDS))
      val mailActor = context.spawn(TenantMailActor(tenant, messageStore, mailRepo), name = "mail-actor")

      def activated(mailActor: ActorRef[EmailCommand]): Behavior[EmailCommand] = {
        println("Activate Tenant Worker")
        Behaviors.receiveMessage[EmailCommand] {
          case Refresh =>
            mailActor ! FetchEmailCommand(tenant.tenant, "", mqttPublisher)
            Behaviors.same

          case WaitResponse =>
            mailActor ! FetchEmailCommand(tenant.tenant, "", mqttPublisher)
            Behaviors.same

          case EmitSendEmailCommand(email, replyTo) =>
            context.log.debug(s"Forward mail to Mail Actor ${email.toEmail}")

            mailActor ! SendEmailCommand(email, tenant.domain, replyTo)
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