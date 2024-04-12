package at.energydash
package services

import admin.mail.{Attachement, SendMailWithInlineAttachmentsRequest}
import domain.dao.{Db, SlickEmailOutboxRepository, TenantConfig}
import domain.email.MockedSMTPProvider

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.jvnet.mock_javamail.Mailbox
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, FileOutputStream}
import java.util.Properties
import javax.mail.internet.{InternetAddress, MimeMultipart}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.Source

class SendMailServiceImplSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {
  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, "smtp.mail.com", 0, "sepp", "password", "", "", true)

  implicit def stringToInternetAddress(string: String): InternetAddress = new InternetAddress(string)

  import scala.concurrent.ExecutionContext.Implicits.global

  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)

  private val mockedSession = javax.mail.Session.getDefaultInstance(new Properties() {
    {
      put("mail.transport.protocol.rfc822", "mocked")
    }
  })
  mockedSession.setProvider(new MockedSMTPProvider)

//  override val testKit = ActorTestKit()
//  override implicit val system: ActorSystem[_] = testKit.system

  "Send Email over gRPC" should {
    "Send HTML Inline MailCommand" in {

      val mailService = new SendMailServiceImpl(mockedSession)

      val fileBody = Source.fromResource("test-html-inline-mail.html").getLines.mkString
//      getClass.getResourceAsStream("Aktivierungsmail-menu-1.png").readAllBytes()
//      val imgAttachment = com.google.protobuf.ByteString.copyFrom(Source.fromResource("Aktivierungsmail-menu-1.png").toArray)
      val imgAttachment = com.google.protobuf.ByteString.copyFrom(getClass.getResourceAsStream("/Aktivierungsmail-menu-1.png").readAllBytes())

      val request = SendMailWithInlineAttachmentsRequest(
        sender = "system@mail.com", recipient = "tester@mail.com",
        subject = "Test Message", htmlBody = fileBody,
        attachments = List(
          Attachement(contentId = Some("image1"), mimeType = "image/png", filename = "image1", content = imgAttachment)))
      val reply = Await.result(mailService.sendMailWithInlineAttachment(request), 5.second)

      println(reply)
      val momsInbox = Mailbox.get("tester@mail.com")
      momsInbox should have size 1

      val momsMsg = momsInbox.get(0)
      momsMsg.writeTo(new FileOutputStream(new File(s"Inline-Html-message.eml")))
      val content = momsMsg.getContent.asInstanceOf[MimeMultipart]
      content.getCount should be(2)
      println(momsMsg.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getDisposition())
      println(momsMsg)
      momsMsg.getSubject should be("Test Message")

      Mailbox.clearAll()
    }
  }

}
