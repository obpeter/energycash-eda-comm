package at.energydash.domain.email

import akka.actor.typed.ActorRef
import akka.util.ByteString
import at.energydash.actor.commands.EmailCommand
import at.energydash.domain.email.Fetcher.MailMessage
import at.energydash.model.EbMsMessage
import courier._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.mail.internet.InternetAddress
import scala.concurrent.{ExecutionContext, Future}

object EmailService {
  case class EmailModel(tenant: String, toEmail: String, subject: String, attachment: ByteString, data: EbMsMessage)

  case class AdminEmailModel(tenant: String, toEmail: String, subject: String, attachement: ByteString)
//  sealed trait Command

  case class EmitSendEmailCommand(email: EmailModel, replyTo: ActorRef[EmailCommand]) extends EmailCommand
  case class SendEmailCommand(email: EmailModel, domain: String, replyTo: ActorRef[EmailCommand]) extends EmailCommand {

    def sendEmail(mailer:  Mailer)(implicit ex: ExecutionContext): Future[Unit] = {
      shippingEmail(mailer)
    }

    private def shippingEmail(mailer: Mailer)(implicit ex: ExecutionContext): Future[Unit] = {
      println(s"About to send Email ${email.tenant}@${domain} to ${email.toEmail}@${domain}")
      val myFormatObj = DateTimeFormatter.ofPattern("yyyyMMdd")
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