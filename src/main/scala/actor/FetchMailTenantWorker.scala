package at.energydash
package actor

import actor.FetchMailActor.{DeleteEmailCommand, FetchEmailCommand}
import actor.commands.{Command, EmailCommand}

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import at.energydash.config.Config
import at.energydash.domain.email.ConfiguredMailer
import at.energydash.domain.email.EmailService.SendEmailCommand

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class FetchMailTenantWorker(timers: TimerScheduler[EmailCommand], tenant: String, supervisor: ActorRef[EmailCommand], messageStore: ActorRef[MessageStorage.Command[_]]) {
  import FetchMailTenantWorker._

  private def setup(): Behavior[EmailCommand] = {
    Behaviors.setup { context => {
      context.log.info("Setup Tenant Worker")

      timers.startTimerWithFixedDelay(TimerKey, Refresh, 1.minute)

      val mailActor = context.spawn(FetchMailActor(tenant, messageStore), name = "mail-actor")

      def activated(mailActor: ActorRef[EmailCommand]): Behavior[EmailCommand] = {
        println("Activate Tenant Worker")
        Behaviors.receiveMessage[EmailCommand] {
          case Refresh =>
            mailActor ! FetchEmailCommand(tenant, "", supervisor)
            Behaviors.same

          case WaitResponse =>
            mailActor ! FetchEmailCommand(tenant, "", supervisor)
            Behaviors.same

          case msg@SendEmailCommand(_, _) =>
            context.log.debug(s"Forward mail to Mail Actor ${msg.email.toEmail}")

            mailActor ! msg
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

  def apply(tenant: String, supervisor: ActorRef[EmailCommand], messageStore: ActorRef[MessageStorage.Command[_]]): Behavior[EmailCommand] = {
    Behaviors.withTimers(timers => new FetchMailTenantWorker(timers, tenant, supervisor, messageStore).setup())
  }
}