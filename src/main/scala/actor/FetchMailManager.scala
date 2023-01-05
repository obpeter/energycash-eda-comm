package at.energydash
package actor

import domain.email.Fetcher

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Scheduler}

import java.util.Date
import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.util.{Failure, Success, Try}
import at.energydash.actor.commands._
import at.energydash.domain.email.Fetcher.MailMessage
import com.typesafe.akka.extension.quartz.QuartzSchedulerTypedExtension

import scala.concurrent.duration.DurationInt

object FetchMailManager {

  def fetcher = Fetcher()

  case class FetchEmailCommand(subject: String, replyTo: ActorRef[EmailCommand]) extends EmailCommand {
    def fetchEmail(searchTerm: String): Try[List[MailMessage]] = {
      Try(
        fetcher.fetch(searchTerm)
      )
    }
  }

  case class DeleteEmailCommand(messageId: String) extends EmailCommand {
    def deleteMail(): Unit = {
      fetcher.deleteById(messageId)
    }
  }

  def apply(supervisor: ActorRef[Command], messageStore: ActorRef[MessageStorage.Command[_]]): Behavior[Command] = Behaviors.setup[Command] { implicit context =>

    val log = context.log

    implicit val ec: ExecutionContextExecutor = context.executionContext
    implicit val timeout: Timeout = Timeout(5.seconds)
    implicit val scheduler: Scheduler = context.system.scheduler

    createQuartzSchedule("[", supervisor)

    Behaviors.receiveMessage[Command] {
      case req: FetchEmailCommand => {
        req.fetchEmail(req.subject) match {
          case Success(msgs) =>
            Future.sequence(msgs.map(m => {
              for {
                _ <- messageStore.ask(ref => MessageStorage.FindById(m.content.message.conversationId, ref)).map {
                  case MessageStorage.MessageNotFound(_) => Future.failed(new Exception("Conversation-id not found"))
                  case MessageStorage.MessageFound(m) => Future(m)
                }
                _ <- messageStore.ask(ref => MessageStorage.AddMessage(m.content.message, ref)).mapTo[MessageStorage.Added]
                mm <- Future {m}
              } yield mm
            })).onComplete {
              case Success(m) => req.replyTo ! FetchEmailResponse(m)
            }
          case Failure(ex) =>
            log.error(ex.getMessage)
            req.replyTo ! ErrorResponse(ex.getMessage)
        }
        Behaviors.same
      }
      case req: DeleteEmailCommand =>
        req.deleteMail()
        Behaviors.same
      case Shutdown =>
        QuartzSchedulerTypedExtension(context.system).shutdown(false)
        Behaviors.stopped
    }
  }

  private def createQuartzSchedule(searchTerm: String, scheduler: ActorRef[Command])(implicit context: ActorContext[Command]): Date = {

    val startDate = QuartzSchedulerTypedExtension(context.system).scheduleTyped(SchedulerName.Every10Seconds.toString,
      context.self,
      FetchEmailCommand(searchTerm, scheduler))

    context.log.info(s"start date: ${startDate.toString}")
    startDate
  }
}
