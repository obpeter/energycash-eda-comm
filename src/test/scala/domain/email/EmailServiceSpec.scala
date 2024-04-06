package at.energydash
package domain.email

import actor.commands.EmailCommand
import actor.{MessageStorage, TenantMailActor}
import domain.email.EmailService.{EmailModel, SendEmailCommand, SendEmailResponse}
import model.EbMsMessage

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.util.ByteString
import org.jvnet.mock_javamail.{Mailbox, MockTransport}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Properties
import javax.mail.Provider
import javax.mail.internet.{InternetAddress, MimeMultipart}
import scala.language.postfixOps
import model.dao.TenantConfig
import domain.dao.{SlickEmailOutboxRepository, Db}

class MockedSMTPProvider
  extends Provider(Provider.Type.TRANSPORT, "mocked", classOf[MockTransport].getName, "Mock", null)


class EmailServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {
  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, "smtp.mail.com", 0, "sepp", "password", "", "", true)
  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  import scala.concurrent.ExecutionContext.Implicits.global
  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)

  private val mockedSession = javax.mail.Session.getDefaultInstance(new Properties() {
    {
      put("mail.transport.protocol.rfc822", "mocked")
    }
  })
  mockedSession.setProvider(new MockedSMTPProvider)

  "Send Email" should {
    "SendMailCommand" in {
      val tenant = "myeeg"
      val mailer = ConfiguredMailer.createMailerFromSession(mockedSession)
      val mailModel = EmailModel(tenant, "mom", "miss you", ByteString("".getBytes),
        EbMsMessage(None, "conversationId", "sender", "receiver", messageCodeVersion=Some("01.00")))
      val messageStore = createTestProbe[MessageStorage.Command[_]]()
      val replyProbe = createTestProbe[EmailCommand]()

      val mailCommand = SendEmailCommand(mailModel, "email.com", replyProbe.ref)

      val emailService = spawn(TenantMailActor(tenantConfig, messageStore.ref, emailRepo))
      emailService ! mailCommand

      replyProbe.expectMessage(SendEmailResponse(EbMsMessage(None, "conversationId", "sender", "receiver", messageCodeVersion=Some("01.00"))))

      val momsInbox = Mailbox.get("mom@email.com")
      momsInbox should have size 1

      val momsMsg = momsInbox.get(0)
      val content = momsMsg.getContent.asInstanceOf[MimeMultipart]
      content.getCount should be(2)
      println(momsMsg.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getDisposition())
      println(momsMsg)
      momsMsg.getSubject should be("miss you")

      Mailbox.clearAll()
    }
  }


}
