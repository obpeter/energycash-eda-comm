package at.energydash
package domain.email

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.util.ByteString
import at.energydash.domain.email.EmailService.{EmailModel, SendEmailCommand, SendEmailResponse}
import at.energydash.model.EbMsMessage
import org.jvnet.mock_javamail.{Mailbox, MockTransport}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Properties
import javax.mail.Provider
import javax.mail.internet.{InternetAddress, MimeMessage, MimeMultipart}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class MockedSMTPProvider
  extends Provider(Provider.Type.TRANSPORT, "mocked", classOf[MockTransport].getName, "Mock", null)


class EmailServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  private val mockedSession = javax.mail.Session.getDefaultInstance(new Properties() {
    {
      put("mail.transport.protocol.rfc822", "mocked")
    }
  })
  mockedSession.setProvider(new MockedSMTPProvider)

  "Send Email" should {
    "SendMailCommand" in {
      val mailer = ConfiguredMailer.createMailerFromSession(mockedSession)
      val mailModel = EmailModel("myeeg", "mom", "miss you", ByteString("".getBytes),
        EbMsMessage(None, "conversationId", "sender", "receiver"))
      val replyProbe = createTestProbe[EmailService.Response]()
      val mailCommand = SendEmailCommand(mailModel, mailer, replyProbe.ref)

      val emailService = spawn(EmailService())
      emailService ! mailCommand

      replyProbe.expectMessage(SendEmailResponse(EbMsMessage(None, "conversationId", "sender", "receiver")))

      val momsInbox = Mailbox.get("mom@email.com")
      momsInbox should have size 1

      val momsMsg = momsInbox.get(0)
      val content = momsMsg.getContent.asInstanceOf[MimeMultipart]
      content.getCount should be(2)
      println(momsMsg.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getDisposition())
      println(momsMsg)
      momsMsg.getSubject should be("miss you")
    }
  }


}
