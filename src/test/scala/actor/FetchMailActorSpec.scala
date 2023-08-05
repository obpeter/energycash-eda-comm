package at.energydash
package actor

import actor.MessageStorage.StoredConversation
import actor.MqttPublisher.{MqttCommand, MqttPublish}
import actor.TenantMailActor.FetchEmailCommand
import domain.dao.model.TenantConfig
import domain.dao.spec.{Db, SlickEmailOutboxRepository}
import domain.email.ConfiguredMailer
import model.enums.EbMsMessageType._
import model.{EbMsMessage, Meter}

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import courier.Multipart
import org.jvnet.mock_javamail.Mailbox
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import javax.mail.Message
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.io.Source
import scala.xml.XML
class FetchMailActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  import scala.concurrent.ExecutionContext.Implicits.global

  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, "smtp.mail.com", 0, "sepp", "password", "", "", true)
  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)

  def perpareEmail(tenant: String, xmlfile: String, subject: String): Unit = {
    val session = ConfiguredMailer.getSession(tenantConfig)

    // prepare Mock - Mailbox
    val attachement = XML.load(Source.fromResource(xmlfile).reader())

    val mailMsg: Message = new MimeMessage(session)
    mailMsg.setSubject(subject)
    mailMsg.setHeader("Message-ID", "1")
    mailMsg.setFrom("test@email.com")
    mailMsg.setContent(Multipart()
      .attachBytes(
        attachement.toString().getBytes, "test.xml", "text/xml")
      .html("Hallo").parts)

    Mailbox.get("sepp@email.com").add(mailMsg)
  }

  def perpareErrorEmail(tenant: String): Unit = {
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
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareEmail("myeeg", "TestECMPLIst.xml", "[EC_PODLIST_01.00 MessageId=123456]")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.FindById] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeMsg.replyTo ! MessageStorage.MessageFound(StoredConversation("RC100130202301231674475740000000030", None))

//      val storeStore = messageStoreProbe.expectMessageType[MessageStorage.AddMessage] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
//      storeStore.replyTo ! MessageStorage.Added(EbMsMessage(messageId = Some("1"), conversationId = "1", sender = "myeeg", receiver = "sepp", messageCode = EbMsMessageType.ZP_LIST))

      replyActor.expectMessageType[MqttPublish]

      Mailbox.clearAll()
    }

    "handle Error EmailMessages" in {
      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareErrorEmail("myeeg")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      replyActor.expectMessageType[MqttPublish]

      Mailbox.clearAll()
    }

    "response with existing conversation id" in {

      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareEmail("myeeg", "ANTWORT_PT.xml", "[CR_REQ_PT_03.00 MessageId=123456678]")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.FindById] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeMsg.replyTo ! MessageStorage.MessageFound(StoredConversation("RC100001202307250000000000000000009",
        Some(EbMsMessage(None, "RC100001202307250000000000000000009", "sender", "receiver", ENERGY_SYNC_RES, None, Some(Meter("meterid123456", None))))))

      val mqttMsg = replyActor.expectMessageType[MqttPublish]

      mqttMsg.mails.head.message.meter.get.meteringPoint shouldBe "meterid123456"
      mqttMsg.mails.head.message.responseData.get.head.ResponseCode.head shouldBe 70

      Mailbox.clearAll()
    }

    "response with not existing conversation id" in {

      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareEmail("myeeg", "ANTWORT_PT.xml", "[CR_REQ_PT_03.00 MessageId=123456678]")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.FindById] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeMsg.replyTo ! MessageStorage.MessageNotFound("RC100001202307250000000000000000009")

      val mqttMsg = replyActor.expectMessageType[MqttPublish]

      mqttMsg.mails.head.message.meter shouldBe None
      mqttMsg.mails.head.message.responseData.get.head.ResponseCode.head shouldBe 70

      Mailbox.clearAll()
    }

    "response with existing conversation id but with all infos" in {

      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.FindById] //(MessageStorage.FindById("RC100130202301231674475740000000030", _))
      storeMsg.replyTo ! MessageStorage.MessageFound(StoredConversation("RC100001202307250000000000000000009",
        Some(EbMsMessage(None, "RC100001202307250000000000000000009", "sender", "receiver", ONLINE_REG_APPROVAL, None, Some(Meter("meterid123456", None))))))

      val mqttMsg = replyActor.expectMessageType[MqttPublish]
      println(mqttMsg)
      mqttMsg.mails.head.message.meter shouldBe None
      mqttMsg.mails.head.message.responseData.get.head.ResponseCode.head shouldBe 175
      mqttMsg.mails.head.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000959561")

      Mailbox.clearAll()
    }
  }

}
