package at.energydash.domain.email

import javax.mail.internet.InternetAddress
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.ByteString
import at.energydash.actor.commands.EmailCommand
import at.energydash.config.Config
import at.energydash.domain.email.Fetcher.MailMessage
import at.energydash.model.EbMsMessage
import courier._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object EmailService {
  case class EmailModel(tenant: String, toEmail: String, subject: String, attachment: ByteString, data: EbMsMessage)
//  sealed trait Command

  case class SendEmailCommand(email: EmailModel, replyTo: ActorRef[EmailCommand]) extends EmailCommand {
    def sendEmail(mailer: Mailer)(implicit ex: ExecutionContext): Future[Unit] = {
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

  case class SendEmailResponse(email: EbMsMessage) extends EmailCommand

  case class SendErrorResponse(tenant: String, message: String) extends EmailCommand

  case class FetchEmailResponse(tenant: String, mails: List[MailMessage]) extends EmailCommand

}