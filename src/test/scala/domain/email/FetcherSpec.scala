package at.energydash
package domain.email

import domain.dao.{Db, SlickEmailOutboxRepository}
import domain.eda.message.CPRequestZPList
import domain.email.Fetcher.{ErrorMessage, FetcherContext, MailMessage, ErrorParseMessage}
import model.EbMsMessage
import model.dao.TenantConfig

import courier.Multipart
import org.jvnet.mock_javamail.Mailbox
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import javax.mail.Message
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.io.Source
import scala.language.implicitConversions
import scala.xml.XML

class FetcherSpec extends AnyWordSpecLike with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, "smtp.mail.com", 0, "sepp", "password", "", "", true)
  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)

  "Email Fetcher" should {
    "Read Email" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      // prepare Mock - Mailbox
      val attachement = XML.load(Source.fromResource("TestECMPLIst.xml").reader())

      val mailMsg: Message = new MimeMessage(session)
      mailMsg.setSubject("[EC_PODLIST_01.00 MessageId=123456]")
      mailMsg.setHeader("Message-ID", "1")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart()
        .attachBytes(
          attachement.toString().getBytes, "test.xml", "text/xml").parts
        /*.html("Hallo").parts*/)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("[", {
        case msg: MailMessage =>
          msg.content shouldBe a[CPRequestZPList]
          msg.messageId shouldBe "123456"
          msg.protocol shouldBe "EC_PODLIST"
          msg.content.message shouldBe a[EbMsMessage]

          msg.content.message.meterList shouldBe defined
          msg.content.message.meterList.get should have size 3
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe true

      Mailbox.clearAll()

//      val filePath = new File("/home/petero/projects/sepphuber/willtesten")
//      if (!filePath.exists()) filePath.mkdirs()
//      val file = new File(filePath, "test.eml")
//      file.createNewFile()
//      new FileOutputStream(file).write(attachement.toString().getBytes())
    }

    "fetch an error response" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      // prepare Mock - Mailbox
      val attachement = XML.load(Source.fromResource("error.xml").reader())

      val mailMsg: Message = new MimeMessage(session)
      mailMsg.setSubject("EDA Mail Adapter - Fehler")
      mailMsg.setHeader("Message-ID", "2")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart()
        .attachBytes(
          attachement.toString().getBytes, "test.xml", "text/xml")
        .html("Hallo").parts)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("", {
        case msg: ErrorMessage =>
          msg.content.message.errorMessage shouldBe Some(
                        """Der Messenger hat eine Fehlermeldung zurueck geliefert. Beschreibung: 'Validation failed.  Reason: 2 XML errors found while validating with (ANFORDERUNG_ECP 01.00/EC_PODLIST_01.00)
                          |   (Line 2 / Column 1154) cvc-minLength-valid: Value &apos;&apos; with length = &apos;0&apos; is not facet-valid with respect to minLength &apos;1&apos; for type &apos;MeteringPoint&apos;.
                          |   (Line 2 / Column 1154) cvc-type.3.1.3: The value &apos;&apos; of element &apos;ct:MeteringPoint&apos; is not valid.'""".stripMargin)
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe true
      Mailbox.clearAll()
    }

    "fetch an error response without attachements" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      val mailMsg: Message = new MimeMessage(session)
      mailMsg.setSubject("EDA Mail Adapter - Fehler")
      mailMsg.setHeader("Message-ID", "2")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart().html("Hallo").parts)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("", {
        case msg: ErrorMessage => msg.content.message.errorMessage shouldBe Some("No Attachement")
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe true

      Mailbox.clearAll()
    }

    "fetch a response without attachements" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      val mailMsg: Message = new MimeMessage(session)
      mailMsg.setSubject("[EC_PODLIST_01.00 MessageId=123456]")
      mailMsg.setHeader("Message-ID", "1")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart().html("Hallo").parts)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("", {
        case msg: ErrorMessage => msg.content.message.errorMessage shouldBe Some("No Attachement")
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe true

      Mailbox.clearAll()
    }

    "fetch a response with multiple attachements" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      val mailMsg: Message = new MimeMessage(session)
      val attachment = XML.load(Source.fromResource("error.xml").reader())

      mailMsg.setSubject("[EC_PODLIST_01.00 MessageId=123456]")
      mailMsg.setHeader("Message-ID", "1")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart()
        .attachBytes(attachment.toString().getBytes, "test.xml", "text/xml")
        .attachBytes(attachment.toString().getBytes, "test1.xml", "text/xml")
        .html("Hallo").parts)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("", {
        case msg: ErrorMessage => msg.content.message.errorMessage shouldBe Some("No Attachement")
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe true

      Mailbox.clearAll()
    }

    "fetch a response with wrong attachement" in {
      val tenant = "myeeg"
      val session = ConfiguredMailer.getSession(tenantConfig)

      val mailMsg: Message = new MimeMessage(session)
      val attachment = XML.load(Source.fromResource("CR_MSG_03.03_AT1234567890.xml").reader())

      mailMsg.setSubject("[CR_MSG_01.00 MessageId=123456]")
      mailMsg.setHeader("Message-ID", "1")
      mailMsg.setFrom("test@email.com")
      mailMsg.setContent(Multipart()
        .attachBytes(attachment.toString().getBytes, "test.xml", "text/xml")
        .html("Hallo").parts)

      Mailbox.get("sepp@email.com").add(mailMsg)

      val fetcher: Fetcher = Fetcher()
      fetcher.fetch("", {
        case msg: ErrorParseMessage => ???
      })(FetcherContext(tenant, session, emailRepo))

      Mailbox.get("sepp@email.com").isEmpty shouldBe false

      Mailbox.clearAll()
    }
  }
}
