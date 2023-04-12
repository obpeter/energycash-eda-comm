package at.energydash
package domain.email

import config.Config
import domain.dao.model.EmailOutbox
import domain.dao.spec.{Db, SlickEmailOutboxRepository}
import domain.eda.message.{EdaErrorMessage, EdaMessage, MessageHelper}
import domain.email.Fetcher.MailerResponseValue

import at.energydash.model.EbMsMessage
import at.energydash.model.enums.{EbMsMessageType, EbMsProcessType}
import com.typesafe.config.{Config => AkkaConfig}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataOutputStream, File, FileOutputStream}
import org.slf4j.LoggerFactory

import java.sql.{Blob, Date, Timestamp}
import javax.mail.internet.{MimeBodyPart, MimeMultipart}
import javax.mail.{Flags, Folder, Message, Multipart, Part, Session, Store}
import javax.mail.search.{AndTerm, FlagTerm, MessageIDTerm, SubjectTerm}
import scala.util.{Failure, Success}

class Fetcher {

  import Fetcher.{MailMessage, ErrorMessage, MailContent, FetcherContext}

  val logger = LoggerFactory.getLogger(classOf[Fetcher])

  def createAndTerm(subject: String) : AndTerm = new AndTerm(Array(new SubjectTerm(""), Fetcher.UNSEENTERM))

  def persist(tenant: String, msgs: Array[Message])(implicit ctx: FetcherContext): Array[Message] = {
    msgs.foreach(m => if(m.getSize > 0) {
      val dataOutputStream = new ByteArrayOutputStream()
      m.writeTo(dataOutputStream)
//      m.writeTo(new FileOutputStream(new File(s"${Config.emailPersistInbox}/${m.getReceivedDate.toString}_${m.getSubject}-${m.getMessageNumber}.eml")))
      ctx.mailRepo.create(EmailOutbox(None, tenant, dataOutputStream.toByteArray, new Timestamp(System.currentTimeMillis())))
    })
    msgs
  }

//  def openInbox(): Folder = {
//    Fetcher.store.connect(Config.imapHost, Config.imapUser, Config.imapPwd)
//    val inbox = Fetcher.store.getFolder("Inbox")
//    inbox.open(Folder.READ_WRITE)
//    inbox
//  }

  def getStore(session: Session): Store = {
    val store = session.getStore()

    store.connect()
    store
  }

  def fetch(searchTerm: String)(implicit ctx: FetcherContext): List[MailContent] = {
    val store = getStore(ctx.session)
    val inbox = store.getFolder("Inbox")
    inbox.open(Folder.READ_WRITE)

    val messages = inbox.search(createAndTerm(searchTerm))
    logger.info(s"[${ctx.tenant}] Emails found ${messages.length}")

    val messageOptions: List[Either[Message, MailContent]] = persist(ctx.tenant, messages).toList.map(buildMessage)
    val msgs: List[MailContent] = messageOptions.flatMap {
      case Left(value:Message) =>
          deleteEmail(value, inbox)
          None
      case Right(right) => Some(right)
    }

    inbox.close(true)
    store.close()

    msgs
  }

  def withAttachement(content: Message): List[MimeBodyPart] = content.getContent match {
    case multipart: MimeMultipart => List.range(0, multipart.getCount)
      .map(i => multipart.getBodyPart(i).asInstanceOf[MimeBodyPart])
      .filter(isAttachment)
    case _ => List.empty
  }

  def buildMessage(m: Message): Either[Message, MailContent] = {
    parseSubject(m.getSubject) match {
      case Some(("ERROR", "")) =>
        Right(ErrorMessage(m.getHeader("Message-ID").toList.head, "ERROR",
          withAttachement(m).take(1).map(body => scala.xml.XML.load(body.getInputStream)) match {
            case List(x) => EdaErrorMessage.fromXML(x) match {
              case Success(e) => e
              case Failure(exception) => EdaErrorMessage(EbMsMessage(messageCode = EbMsMessageType.ERROR_MESSAGE, conversationId = "1", messageId = None,
                sender = "", receiver = "", errorMessage = Some(exception.getMessage)))
            }
            case _ => EdaErrorMessage.fromXML(<EDASendError><ReasonText>No Attachement</ReasonText></EDASendError>) match {
              case Success(e) => e
              case Failure(exception) => EdaErrorMessage(EbMsMessage(messageCode = EbMsMessageType.ERROR_MESSAGE, conversationId = "1", messageId = None,
                sender = "", receiver = "", errorMessage = Some(exception.getMessage)))
            }
          }))
      case Some((protocol, messageId)) =>
        withAttachement(m) match {
          case List(body) => MessageHelper.getEdaMessageFromHeader(EbMsProcessType.withName(protocol))
            .fromXML(scala.xml.XML.load(body.getInputStream)) match {
              case Success(value) =>
                Right(
                  MailMessage(
                    m.getHeader("Message-ID").toList.head,
                    m.getFrom.head.toString,
                    protocol,
                    messageId,
                    value
                  )
                )
              case Failure(exception) =>
                logger.error(exception.getMessage)
                Left(m)
              }
          case _ => Right(ErrorMessage(
            m.getHeader("Message-ID").toList.head,
            "ERROR", EdaErrorMessage.fromXML(
              <EDASendError>
                <ReasonText>No Attachement</ReasonText>
              </EDASendError>) match {
            case Success(e) => e
            case Failure(exception) => EdaErrorMessage(EbMsMessage(messageCode = EbMsMessageType.ERROR_MESSAGE, conversationId = "1", messageId = None,
              sender = "", receiver = "", errorMessage = Some(exception.getMessage)))
            }))
        }
      case _ => Left(m)
    }
  }

//  def getMetadata(m: Message): EmailEnvelop = {
//    m.getDataHandler
//    EmailEnvelop(m.getHeader("Message-ID").toList.head, m.getSubject, getProtocol(m.getSubject), m, m.getContent.asInstanceOf[Multipart])
//}

  def parseSubject(subject: String): Option[(String, String)] = {
    val pattern = """\[([A-Za-z_-]*)(?:_\d+\.\d+){0,1} MessageId=(.*)\]""".r
    println(s"Subject: ${subject}")
    try {
      val pattern(protocol, messageId) = subject
      if (protocol.isEmpty || messageId.isEmpty) None else Some(protocol, messageId)
    } catch {
      case e: MatchError =>
        logger.error(s"Error Subject: ${e.getMessage()}")
        Some("ERROR", "")
      case _: Throwable =>
        None
    }
  }

//  def getProtocol(subject: String): Option[String] = {
//    val pattern = """\[([A-Za-z_-]*) MessageId=.*\]""".r
//    println(s"subject: ${subject}")
//    try {
//      val pattern(protocol) = subject.toString
//      if(protocol.isEmpty) None else Some(protocol)
//    } catch {
//      case e:MatchError => None
//    }
//  }

  def extractAttachments(multiPart: Multipart): List[MimeBodyPart] = {
    List.range(0, multiPart.getCount)
      .map(i => multiPart.getBodyPart(i).asInstanceOf[MimeBodyPart])
      .filter(isAttachment)
  }

  def isAttachment(b: MimeBodyPart): Boolean = {
    Part.ATTACHMENT.equalsIgnoreCase(b.getDisposition)
  }

  def deleteEmail(m: Message, inbox: Folder): Unit = {
    val id = m.getHeader("Message-ID").toList.head
    try {
      val term = new MessageIDTerm(id)
      inbox.search(term).foreach(m => {
        m.setFlag(Flags.Flag.DELETED, true)
      })
      logger.info(s"Delete Email with id ${id}")
    } catch {
      case e: Exception => {
        logger.error(e.getMessage)
      }
    }
  }

  def deleteById(id: String)(implicit ctx: FetcherContext): Unit = {
    val store = getStore(ctx.session)
    val inbox = store.getFolder("Inbox")
    inbox.open(Folder.READ_WRITE)
    try {
      val term = new MessageIDTerm(id)
      inbox.search(term).foreach(m => {
        m.setFlag(Flags.Flag.DELETED, true)
      })
      logger.info(s"Delete Email with id ${id}")
    } catch {
      case e: Exception => {
        logger.error(e.getMessage)
      }
    } finally {
      inbox.close(true)
      store.close()
    }
  }
}

object Fetcher {

  def apply() = new Fetcher()
  val UNSEENTERM = new FlagTerm(new Flags(Flags.Flag.SEEN), false)

  case class EmailEnvelop(id: String, subject: String, protocol: Option[String], msg: Message, content: Multipart)
  trait MailContent
  case class MailMessage(id: String, from: String, protocol: String, messageId: String, content: EdaMessage[_]) extends MailContent
  case class ErrorMessage(id: String, protocol: String, content: EdaMessage[_]) extends MailContent
  case class FetcherContext(tenant: String, session: Session, mailRepo: SlickEmailOutboxRepository)

  sealed trait MailerResponseValue
  case class MailMessageList(response: List[MailMessage]) extends MailerResponseValue
  case class ErrorMessageList(response: List[ErrorMessage]) extends MailerResponseValue

}