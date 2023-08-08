package at.energydash
package actor

import actor.MqttPublisher.{EdaNotification, MqttCommand, MqttPublish, MqttPublishError}
import actor.commands.EmailCommand
import domain.dao.SlickEmailOutboxRepository
import domain.email.EmailService.{SendEmailCommand, SendEmailResponse, SendErrorResponse}
import domain.email.Fetcher.{ErrorMessage, FetcherContext, MailContent, MailMessage}
import domain.email.{ConfiguredMailer, Fetcher}
import model.dao.TenantConfig

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class TenantMailActor(tenantConfig: TenantConfig, messageStore: ActorRef[MessageStorage.Command[_]], mailRepo: SlickEmailOutboxRepository) {

  import TenantMailActor._

  implicit val timeout: Timeout = Timeout(5.seconds)
  var logger: Logger = LoggerFactory.getLogger(classOf[TenantMailActor])

  var tenant: String = tenantConfig.tenant
//  val config: com.typesafe.config.Config = Config.getMailSessionConfig(tenant)
  private val mailSession = ConfiguredMailer.getSession(tenantConfig)

  implicit val mailContext: FetcherContext = FetcherContext(tenant, mailSession, mailRepo)

  def start: Behavior[EmailCommand] = Behaviors.setup[EmailCommand] { context => {
    import context.{executionContext, system}

    context.setLoggerName(TenantMailActor.getClass)

    def work(): Behavior[EmailCommand] = {
      Behaviors.receiveMessage {
        case req: FetchEmailCommand =>
          req.fetchEmail(req.subject) match {
            case Success(msgs) => {
                Future.sequence(msgs.map {
                  case m: MailMessage =>
                    for {
                      sm <- messageStore.ask(ref => MessageStorage.FindById(m.content.message.conversationId, ref)).map {
                        case MessageStorage.MessageNotFound(_) => m.content
                        case MessageStorage.MessageFound(storedMessage) => mergeEbmsMessage(storedMessage.message, m.content)
                      }
                      _ <- Future {Fetcher().deleteById(m.id)}
                    } yield EdaNotification(m.protocol, sm)
                  case m: ErrorMessage =>
                    for {
                      mm <- Future {
                        Fetcher().deleteById(m.id)
                        m.content
                      }
                    } yield EdaNotification("ERROR", mm)
                }).onComplete {
                  case Success(n: List[EdaNotification]) => req.replyTo ! MqttPublish(n)
                  case Failure(e) => req.replyTo ! MqttPublishError(req.tenant, e.getMessage)
                }
            }
            case Failure(ex) =>
              context.log.error(ex.getMessage)
              req.replyTo ! MqttPublishError(req.tenant, ex.getMessage)
          }
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
          req.deleteMail()
          Behaviors.same
      }
    }
    work()
  }}
}

object TenantMailActor {
  case class FetchEmailCommand(tenant: String, subject: String, replyTo: ActorRef[MqttCommand]) extends EmailCommand {
    import com.typesafe.config.Config
    def fetchEmail(searchTerm: String)(implicit ex: ExecutionContext, ctx: FetcherContext): Try[List[MailContent]] = {
      Try(
        Fetcher().fetch(searchTerm)
      )
    }
  }

  case class DeleteEmailCommand(tenant: String, messageId: String) extends EmailCommand {
    def deleteMail()(implicit ctx: FetcherContext): Unit = {
      Fetcher().deleteById(messageId)
    }
  }

  def apply(tenantConfig: TenantConfig, messageStore: ActorRef[MessageStorage.Command[_]], mailRepo: SlickEmailOutboxRepository): Behavior[EmailCommand] = {
    new TenantMailActor(tenantConfig, messageStore, mailRepo).start
  }
}
