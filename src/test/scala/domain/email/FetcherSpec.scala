package at.energydash
package domain.email

import at.energydash.config.Config
import at.energydash.domain.eda.message.CPRequestZPListMessage
import at.energydash.model.EbMsMessage

import javax.mail.Message
import courier.Multipart
import org.jvnet.mock_javamail.Mailbox
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.io.Source
import scala.xml.XML

class FetcherSpec extends AnyFlatSpec with Matchers {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  "it" should "Send Email" in {
    val config = Config.getMailSessionConfig("myeeg")
    val session = ConfiguredMailer.getSession(config)

    // prepare Mock - Mailbox
   val attachement = XML.load(Source.fromResource("TestECMPLIst.xml").reader())

    val mailMsg: Message = new MimeMessage(session)
    mailMsg.setSubject("[EC_PODLIST_01.00 MessageId=123456]")
    mailMsg.setHeader("Message-ID", "1")
    mailMsg.setFrom("test@email.com")
    mailMsg.setContent(Multipart()
      .attachBytes(
        attachement.toString().getBytes, "test.xml", "text/xml")
      .html("Hallo").parts)

    Mailbox.get("sepp@email.com").add(mailMsg)

    val fetcher: Fetcher = Fetcher()
    val msgs = fetcher.fetch("myeeg", "[")

    println(msgs)

    msgs should have size 1
    val msg = msgs.head
    msg.content shouldBe a[CPRequestZPListMessage]
    msg.messageId shouldBe "123456"
    msg.protocol shouldBe "EC_PODLIST"
    msg.content.message shouldBe a[EbMsMessage]

    msg.content.message.meterList shouldBe defined
    msg.content.message.meterList.get should have size 3
  }
}
