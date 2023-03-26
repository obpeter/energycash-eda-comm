package at.energydash
package actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import at.energydash.actor.FetchMailActor.FetchEmailCommand
import at.energydash.actor.MessageStorage.StoredConversation
import at.energydash.actor.MqttPublisher.{MqttCommand, MqttPublish}
import at.energydash.actor.commands.EmailCommand
import at.energydash.config.Config
import at.energydash.domain.dao.model.TenantConfig
import at.energydash.domain.dao.spec.{Db, SlickEmailOutboxRepository, SlickTenantConfigRepository}
import at.energydash.domain.email.ConfiguredMailer
import at.energydash.domain.email.Fetcher.{MailContent, MailMessage}
import at.energydash.model.EbMsMessage
import at.energydash.model.enums.EbMsMessageType
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import javax.mail.Message
import courier.Multipart
import org.jvnet.mock_javamail.Mailbox

import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.xml.XML
import scala.io.Source
class FetchMailActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  import scala.concurrent.ExecutionContext.Implicits.global

  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, 0, "sepp", "password", "", "", true)
  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)

  def perpareEmail(tenant: String): Unit = {
    val config = Config.getMailSessionConfig(tenant)
    val session = ConfiguredMailer.getSession(tenantConfig)

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
  }

  def perpareErrorEmail(tenant: String): Unit = {
    val config = Config.getMailSessionConfig(tenant)
    val session = ConfiguredMailer.getSession(tenantConfig)

    val mailMsg: Message = new MimeMessage(session)
    mailMsg.setSubject("Just a Mail")
    mailMsg.setHeader("Message-ID", "1")
    mailMsg.setFrom("test@email.com")
    mailMsg.setContent(Multipart()
      .html("Hallo").parts)

    Mailbox.get("sepp@email.com").add(mailMsg)
  }

  "Fetch Mail Actor" should {
    "handle Email Success Messages" in {
      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(FetchMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareEmail("myeeg")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.FindById] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeMsg.replyTo ! MessageStorage.MessageFound(StoredConversation("RC100130202301231674475740000000030", Map.empty))

      val storeStore = messageStoreProbe.expectMessageType[MessageStorage.AddMessage] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeStore.replyTo ! MessageStorage.Added(EbMsMessage(messageId = Some("1"), conversationId = "1", sender = "myeeg", receiver = "sepp", messageCode = EbMsMessageType.ZP_LIST))

      replyActor.expectMessageType[MqttPublish]

      Mailbox.clearAll()
    }

    "handle Error EmailMessages" in {
      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(FetchMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareErrorEmail("myeeg")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      replyActor.expectMessageType[MqttPublish]

      Mailbox.clearAll()
    }
  }

}
