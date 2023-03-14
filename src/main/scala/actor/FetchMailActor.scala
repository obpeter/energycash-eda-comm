package at.energydash
package actor

import actor.commands.{EmailCommand, ErrorResponse}
import domain.email.EmailService.{FetchEmailResponse, SendEmailCommand, SendEmailResponse, SendErrorResponse}
import domain.email.Fetcher.{ErrorMessage, FetcherContext, MailContent, MailMessage}
import domain.email.{ConfiguredMailer, Fetcher}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import org.slf4j.Logger

import javax.mail.Session
import at.energydash.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class FetchMailActor(tenant: String, messageStore: ActorRef[MessageStorage.Command[_]]) {

  import FetchMailActor._

  implicit val timeout: Timeout = Timeout(5.seconds)
  var logger: Logger = LoggerFactory.getLogger(classOf[FetchMailActor])

  val config: com.typesafe.config.Config = Config.getMailSessionConfig(tenant)
  private val mailSession = ConfiguredMailer.getSession(tenant, config)

  def start: Behavior[EmailCommand] = Behaviors.setup[EmailCommand] { context => {
    import context.executionContext
    import context.system

    def work(): Behavior[EmailCommand] = {
      Behaviors.receiveMessage {
        case req: FetchEmailCommand =>
          req.fetchEmail(mailSession, config, req.subject) match {
            case Success(msgs) => msgs match {
              case messages: List[MailMessage] =>
                Future.sequence(messages.map(m => {
                  for {
                    _ <- messageStore.ask(ref => MessageStorage.FindById(m.content.message.conversationId, ref)).map {
                      case MessageStorage.MessageNotFound(_) => Future.failed(new Exception("Conversation-id not found"))
                      case MessageStorage.MessageFound(m) => Future(m)
                    }
                    _ <- messageStore.ask(ref => MessageStorage.AddMessage(m.content.message, ref)).mapTo[MessageStorage.Added]
                    mm <- Future {
                      m
                    }
                  } yield mm
                })).onComplete {
                  case Success(m) => req.replyTo ! FetchEmailResponse(req.tenant, m)
                  case Failure(e) => req.replyTo ! ErrorResponse(e.getMessage)
                }
              case errors: List[ErrorMessage] =>
                req.replyTo ! ErrorResponse(errors.mkString)
            }
            case Failure(ex) =>
              context.log.error(ex.getMessage)
              req.replyTo ! ErrorResponse(ex.getMessage)
          }
//          req.replyTo ! ErrorResponse("not implemented")
          Behaviors.same
        case req: SendEmailCommand =>
          context.log.info(s"Send Mail to ${req.email.toEmail}")
          req.sendEmail(ConfiguredMailer.createMailerFromSession(mailSession)).onComplete {
            case Success(_) =>
              logger.info(s"Sent Mail to ${req.email.toEmail}")
              req.replyTo ! SendEmailResponse(req.email.data)
            case Failure(ex) =>
              req.replyTo ! SendErrorResponse(tenant, "Error Occured  " + ex.getMessage)
          }
          Behaviors.same
        case req: DeleteEmailCommand =>
          req.deleteMail(mailSession, config)
          Behaviors.same
      }
    }
    work()
  }}
}

object FetchMailActor {
  case class FetchEmailCommand(tenant: String, subject: String, replyTo: ActorRef[EmailCommand]) extends EmailCommand {
    import com.typesafe.config.Config
    def fetchEmail(session: Session, config: Config, searchTerm: String): Try[List[MailContent]] = {
      implicit var ctx: FetcherContext = FetcherContext(tenant, session, config)
      Try(
        Fetcher().fetch(searchTerm)
      )
    }
  }

  case class DeleteEmailCommand(tenant: String, messageId: String) extends EmailCommand {
    import com.typesafe.config.Config
    def deleteMail(session: Session, config: Config): Unit = {
      implicit var ctx: FetcherContext = FetcherContext(tenant, session, config)
      Fetcher().deleteById(messageId)
    }
  }

  def apply(tenant: String, messageStore: ActorRef[MessageStorage.Command[_]]): Behavior[EmailCommand] = {
    new FetchMailActor(tenant, messageStore).start
  }
}
