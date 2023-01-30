package at.energydash
package domain.email

import at.energydash.config.Config
import at.energydash.domain.eda.message.{EdaMessage, MessageHelper}
import at.energydash.model.enums.{EbMsMessageType, EbMsProcessType}

import java.io.{File, FileOutputStream}
import java.util.Properties
import com.typesafe.scalalogging.Logger

import javax.mail.Quota.Resource
import javax.mail.internet.{MimeBodyPart, MimeMultipart}
import javax.mail.{Authenticator, BodyPart, Flags, Folder, Message, Multipart, Part, PasswordAuthentication, Session, Store}
import javax.mail.search.{AndTerm, FlagTerm, MessageIDTerm, SubjectTerm}
import scala.util.{Failure, Success}

class Fetcher {

  import Fetcher.{EmailEnvelop, MailMessage}

  val logger: Logger = Logger("EmailClient")

  def createAndTerm(subject: String) : AndTerm = new AndTerm(Array(new SubjectTerm(""), Fetcher.UNSEENTERM))

  def persist(msgs: Array[Message]): Array[Message] = {
    msgs.foreach(m => if(m.getSize > 0) {
      m.writeTo(new FileOutputStream(new File(s"${Config.emailPersistInbox}/${m.getReceivedDate.toString}_${m.getSubject}-${m.getMessageNumber}.eml")))
    })
    msgs
  }

//  def openInbox(): Folder = {
//    Fetcher.store.connect(Config.imapHost, Config.imapUser, Config.imapPwd)
//    val inbox = Fetcher.store.getFolder("Inbox")
//    inbox.open(Folder.READ_WRITE)
//    inbox
//  }

  def getStore(tenant: String): Store = {
    val config = Config.getMailSessionConfig(tenant)

//    Session.getInstance(System.getProperties(), null) //
    val session = ConfiguredMailer.getSession(config)
    val store = session.getStore()

    store.connect(
      config.getString("javaxmail.mail.imap.host"),
      config.getString("authenticator.username"),
      config.getString("authenticator.password"))
    store
  }

  def fetch(tenant: String, searchTerm: String): List[MailMessage] = {
    val store = getStore(tenant)
    val inbox = store.getFolder("Inbox")
    inbox.open(Folder.READ_ONLY)

    val messages = inbox.search(createAndTerm(searchTerm))
    logger.info(s"Emails found ${messages.length}")

    val msgs = persist(messages).toList.map(buildMessage).collect {
      case Left(value) =>
        deleteEmail(value, inbox)
        Left(value)
      case r => r
    } collect {
      case Right(value) => value
    }

    inbox.close(true)
    store.close()

    msgs
  }

  def buildMessage(m: Message): Either[Message, MailMessage] = {
    parseSubject(m.getSubject) match {
      case Some((protocol, messageId)) => {
        m.getContent match {
          case multipart: MimeMultipart  => {
            val bodypart = List.range(0, multipart.getCount)
              .map(i => multipart.getBodyPart(i).asInstanceOf[MimeBodyPart])
              .filter(isAttachment)
            if (bodypart.nonEmpty) {
              val xmlFile = scala.xml.XML.load(bodypart.head.getInputStream)
              MessageHelper.getEdaMessageFromHeader(EbMsProcessType.withName(protocol)).fromXML(xmlFile) match {
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
            } else {
              logger.error("Empty Body-part! Probably no Attachment")
              Left(m)
            }
          }
          case _ =>
            logger.error("No Multipart in Mail")
            Left(m)

        }
      }
      case None =>
        logger.error("Wrong Subject")
        Left(m)
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
      val pattern(protocol, messageId) = subject.toString
      if (protocol.isEmpty || messageId.isEmpty) None else Some(protocol, messageId)
    } catch {
      case e: MatchError =>
        println(s"................ Error Subject: ${e}")
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

  def deleteById(tenant: String, id: String): Unit = {
    val store = getStore(tenant)
    val inbox = store.getFolder("Inbox")
    inbox.open(Folder.READ_ONLY)

    try {
      val term = new MessageIDTerm(id)
      inbox.search(term).foreach(m => {
        m.setFlag(Flags.Flag.DELETED, true)
      })
      inbox.close(true)
      logger.info(s"Delete Email with id ${id}")
    } catch {
      case e: Exception => {
        logger.error(e.getMessage)
        inbox.close()
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

  case class MailMessage(id: String, from: String, protocol: String, messageId: String, content: EdaMessage[_])

//  private class MailAuthenticator(username: String, password: String) extends Authenticator {
//    override protected def getPasswordAuthentication: PasswordAuthentication = new PasswordAuthentication(username, password)
//  }

//  val props: Properties = System.getProperties
//  props.setProperty("mail.store.protocol", "imaps")
//  props.setProperty("mail.imap.ssl.enable", "true")
//  props.put("mail.imap.port", Config.imapPort);
//  props.put("mail.imap.starttls.enable", "true");
//  props.put("mail.imap.ssl.trust", Config.imapHost);
//
//  val session: Session = Session.getInstance(props, new MailAuthenticator(Config.imapUser, Config.imapPwd))
//  val store: Store = session.getStore("imaps")

//  def closeStore(): Unit = store.close()
}