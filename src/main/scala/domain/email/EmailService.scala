package at.energydash.domain.email

import javax.mail.internet.InternetAddress
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.ByteString
import at.energydash.config.Config
import at.energydash.model.EbMsMessage

import courier._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EmailService {
  case class EmailModel(tenant: String, toEmail: String, subject: String, attachment: ByteString, data: EbMsMessage)
  sealed trait Command

  case class SendEmailCommand(email: EmailModel, mailer: Mailer, replyTo: ActorRef[Response])(implicit ec: ExecutionContext) extends Command {
    def sendEmail(): Future[Unit] = {
      println("About to send Email")

//      val mailer = Mailer(MailSession.getInstance(new Properties())).session
//        .host(Config.smtpHost)
//        .port(Config.smtpPort)
//        .auth(true)
//        .as(Config.smtpUser, Config.smtpPassword)
//        .ssl(true)()

      val myFormatObj = DateTimeFormatter.ofPattern("yyyyMMdd")
      val domain = Config.emailDomain(email.tenant)
      val envelope = Envelope
        .from(new InternetAddress(s"${email.tenant}@${domain}"))
        .to(new InternetAddress(s"${email.toEmail}@${domain}"))
        .subject(email.subject)
        .content(Multipart()
          .attachBytes(email.attachment.toArray,
            s"${LocalDateTime.now().format(myFormatObj)}_${email.data.messageCode.toString}_${email.data.sender}.xml", "text/xml")
          .html(s"Attachment for Process ${email.data.messageCode.toString}"))
      mailer(envelope)
    }
  }

  sealed trait Response

  case class SendEmailResponse(email: EbMsMessage) extends Response

  case class ErrorResponse(message: String) extends Response

  def apply(): Behavior[Command] =
    Behaviors.receive({ (context, message) =>
      implicit val ec = context.executionContext
      val log = context.log

      message match {

        case req: SendEmailCommand => {
            req.sendEmail().onComplete {
              case Success(_) =>
                req.replyTo ! SendEmailResponse(req.email.data)
              case Failure(ex) =>
                log.error("Error Occured  " + ex.getMessage)
                req.replyTo ! ErrorResponse("Error Occured  " + ex.getMessage)
            }
          }
          Behaviors.same
      }
    })

}