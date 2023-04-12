package at.energydash
package services

import admin.excel.{ExcelAdminService, SendExcelReply, SendExcelRequest}

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.{ByteString, Timeout}
import at.energydash.domain.email.ConfiguredMailer
import courier.{Envelope, Mailer, Multipart}

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
   * Sends a greeting
   */
  override def sendExcel(in: SendExcelRequest): Future[SendExcelReply] = {
    println(s"Receive message: ${in.filename}")
    val attachment = ByteString(in.content.toByteArray)
    val subject = s"EEG - Excel Report ${in.tenant}"
    val to = in.recipient
    val from = s"${in.tenant}@ourproject.at"

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val adminMail = AdminMail(from, to, subject, None,
      Some(MailAttachment(in.filename, "application/octet-stream", attachment)))

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
    val envelope = Envelope
      .from(new InternetAddress(s"${email.from}"))
      .to(new InternetAddress(s"${email.to}"))
      .subject(email.subject)
      .content(
        (email.attachment match {
          case Some(a) => Multipart().attachBytes(a.content.toArray, a.filename, a.mimeType)
          case None => Multipart()
        })
        .html(email.body.getOrElse(""))
      )

    mailer(envelope)
  }
}
