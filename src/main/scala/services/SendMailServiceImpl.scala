package at.energydash
package services

import admin.mail.{SendMailReply, SendMailRequest, SendMailService, SendMailWithInlineAttachmentsRequest}
import domain.email.ConfiguredMailer

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.{ByteString, Timeout}
import courier.{Envelope, Mailer, Multipart}

import javax.mail.Session
import javax.mail.internet.{InternetAddress, MimeBodyPart}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

case class InlineAttachment(contentId: String, filename: String, mimeType: String, content: ByteString)
case class MailInlineMessage(from: String, to: String, subject: String, htmlBody: String, inlineContent: Seq[InlineAttachment])

class SendMailServiceImpl(session: Session)(implicit val system: ActorSystem[_]) extends SendMailService {
  implicit val timeout: Timeout = 10.seconds
  implicit val sch: Scheduler = system.scheduler
  import system._

  /**
   * Sends a greeting
   */
  override def sendMailWithInlineAttachment(in: SendMailWithInlineAttachmentsRequest): Future[SendMailReply] = {
    val from = "no-reply@eegfaktura.at"
    val inlineMail = MailInlineMessage(
      from = from, to = in.recipient, subject = in.subject,
      htmlBody = in.htmlBody, inlineContent = in.attachments.flatMap(a => if (a.contentId.isEmpty) None else Some(InlineAttachment(a.contentId.get, a.filename, a.mimeType, ByteString(a.content.toByteArray)))))

    shippingInlineHtmlEmail(ConfiguredMailer.createMailerFromSession(session), inlineMail).transformWith {
      case Success(_) => Future(SendMailReply(200, Some("Email sent")))
      case Failure(exception) => {
        println(exception.toString)
        Future(SendMailReply(500, Some(exception.toString)))
      }
    }
  }

  override def sendMail(in: SendMailRequest): Future[SendMailReply] = ???

  private def shippingInlineHtmlEmail(mailer: Mailer, email: MailInlineMessage)(implicit ec: ExecutionContext): Future[Unit] = {

    val mailContent = email.inlineContent.foldLeft(Multipart(subtype = "related").html(email.htmlBody))((a, b) => {
      val imagePart = new MimeBodyPart()
      imagePart.setContentID(s"<${b.contentId}>")
      imagePart.setDisposition("inline")
      imagePart.setContent(b.content.toArray, b.mimeType)
      a.add(imagePart)
    })

    val envelope = Envelope
      .from(new InternetAddress(s"${email.from}"))
      .to(new InternetAddress(s"${email.to}"))
      .subject(email.subject)
      .content(mailContent)

    mailer(envelope)(ec)
  }
}
