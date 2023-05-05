package at.energydash
package services

import admin.excel.{ExcelAdminService, SendExcelReply, SendExcelRequest}
import domain.email.ConfiguredMailer

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.{ByteString, Timeout}
import courier.{Envelope, Mailer, Multipart}

import java.nio.charset.Charset
import javax.mail.Session
import javax.mail.internet.InternetAddress
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class MailAttachment(filename: String, mimeType: String, content: ByteString)
case class AdminMail(from: String, to: String, subject: String, body: Option[String], attachment: Option[MailAttachment])

class ExcelServiceImpl(session: Session, implicit val system: ActorSystem[_]) extends ExcelAdminService {

  implicit val timeout: Timeout = 10.seconds
  implicit val sch: Scheduler = system.scheduler

  /**
   * Sends a mail
   */
  override def sendExcel(in: SendExcelRequest): Future[SendExcelReply] = {
    println(s"Receive message: ${in.filename}")
    val subject = in.subject
    val to = in.recipient
//    val from = s"${in.tenant}@ourproject.at"
    val from = "admin@ourproject.at"

    val mailAttachment = in.filename match {
      case Some(f) => in.content.map(c => MailAttachment(f, "application/octet-stream", ByteString(c.toByteArray)))
      case None => None
    }

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val adminMail = AdminMail(from, to, subject, in.body.map(b=>b.toStringUtf8), mailAttachment)

//    Future(SendExcelReply("Email sent"))
    shippingEmail(ConfiguredMailer.createMailerFromSession(session), adminMail).transformWith {
      case Success(_) => Future(SendExcelReply("Email sent"))
      case Failure(exception) => {
        println(exception.toString)
        Future(SendExcelReply(exception.toString))
      }
    }
  }

  private def shippingEmail(mailer: Mailer, email: AdminMail)(implicit ex: ExecutionContext): Future[Unit] = {
    println(s"About to send Email ${email.from} to ${email.to}")
//    val myFormatObj = DateTimeFormatter.ofPattern("yyyyMMdd")
    var envelope = Envelope
      .from(new InternetAddress(s"${email.from}"))
      .to(new InternetAddress(s"${email.to}"))
      .subject(email.subject)
//      .content(Multipart().html())
//      .content(
//        (email.attachment match {
//          case Some(a) => Multipart().attachBytes(a.content.toArray, a.filename, a.mimeType)
//          case None => Multipart()
//        })
//        .html(email.body.getOrElse(""))
//      )
    envelope = email.body.fold(envelope)(b => envelope.content(Multipart().html(b, Charset.forName("UTF-8"))))
    envelope = email.attachment.fold(envelope)(a => envelope.content(Multipart().attachBytes(a.content.toArray, a.filename, a.mimeType)))

    mailer(envelope)
  }
}
