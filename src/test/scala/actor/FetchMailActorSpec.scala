package at.energydash
package actor

import actor.MessageStorage.StoredConversation
import actor.MqttPublisher.{MqttCommand, MqttPublish}
import actor.TenantMailActor.FetchEmailCommand
import domain.dao.{Db, SlickEmailOutboxRepository}
import domain.email.ConfiguredMailer
import model.dao.TenantConfig
import model.enums.EbMsMessageType._
import model.{EbMsMessage, Meter}

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import courier.Multipart
import org.jvnet.mock_javamail.Mailbox
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.FileInputStream
import java.nio.file.{FileSystems, Files}
import javax.mail.Message
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.collection.JavaConverters._
import scala.io.Source
import scala.xml.XML

class FetchMailActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  implicit def stringToInternetAddress(string:String):InternetAddress = new InternetAddress(string)

  import scala.concurrent.ExecutionContext.Implicits.global

  val tenantConfig = TenantConfig("myeeg", "email.com", "email.com", 0, "smtp.mail.com", 0, "sepp", "password", "", "", true)
  val emailRepo = new SlickEmailOutboxRepository(Db.getConfig)
  val messageStore = spawn(MessageStorage())
  val rand = new scala.util.Random

//  override def afterAll: Unit = {
//    TestKit.shutdownActorSystem(system)
//  }
  def loadManyMails(): Unit = {
    val session = ConfiguredMailer.getSession(tenantConfig)

    val dir = FileSystems.getDefault.getPath("/home/petero/projects/energycash/scripts/rc100298-aug")
    val emails = Files.walk(dir).iterator().asScala.filter(Files.isRegularFile(_))
      .map(f => new MimeMessage(session, new FileInputStream(f.toFile)))

    Mailbox.get("sepp@email.com").addAll(emails.toSeq.asJava)
  }

  def perpareEmail(tenant: String, xmlfile: String, subject: String): Unit = {
    val session = ConfiguredMailer.getSession(tenantConfig)

    // prepare Mock - Mailbox
    val attachement = XML.load(Source.fromResource(xmlfile).reader())

    val mailMsg: Message = new MimeMessage(session)
    mailMsg.setSubject(subject)
    mailMsg.setHeader("Message-ID", rand.nextLong(100000).toString)
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

//      testKit.stop(mailActor.ref)

      Mailbox.clearAll()
    }

    "handle Error EmailMessages" in {
      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
      val mailActor = spawn(TenantMailActor(tenantConfig, messageStoreProbe.ref, emailRepo))
      val replyActor = createTestProbe[MqttCommand]()

      perpareErrorEmail("myeeg")

      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)

      replyActor.expectMessageType[MqttPublish]

//      testKit.stop(mailActor.ref)
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
        Some(EbMsMessage(None, "RC100001202307250000000000000000009", "sender", "receiver", ENERGY_SYNC_RES, messageCodeVersion=Some("01.00"), None, Some(Meter("meterid123456", None))))))

      val mqttMsg = replyActor.expectMessageType[MqttPublish]

      mqttMsg.mails.head.message.message.meter.get.meteringPoint shouldBe "meterid123456"
      mqttMsg.mails.head.message.message.responseData.get.head.ResponseCode.head shouldBe 70

//      testKit.stop(mailActor.ref)
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

      mqttMsg.mails.head.message.message.meter shouldBe None
      mqttMsg.mails.head.message.message.responseData.get.head.ResponseCode.head shouldBe 70

//      testKit.stop(mailActor.ref)
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
        Some(EbMsMessage(None, "RC100001202307250000000000000000009", "sender", "receiver", ONLINE_REG_APPROVAL, messageCodeVersion=Some("01.00"), None, Some(Meter("meterid123456", None))))))

      val mqttMsg = replyActor.expectMessageType[MqttPublish]
      println(mqttMsg)
      mqttMsg.mails.head.message.message.meter shouldBe None
      mqttMsg.mails.head.message.message.responseData.get.head.ResponseCode.head shouldBe 175
      mqttMsg.mails.head.message.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000959561")

//      testKit.stop(mailActor.ref)
      Mailbox.clearAll()
    }

//    "fetch many emails" in {
//
////      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
//
//      val mailActor = spawn(TenantMailActor(tenantConfig, messageStore, emailRepo))
//      val replyActor = createTestProbe[MqttCommand]()
//
//      (1 to 1000).foreach(_ => perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]"))
//
//      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)
//
//      val mqttMsg = replyActor.receiveMessage(5.minutes)
//
////      mqttMsg match {
////        case MqttPublish(mails) =>
////          mails.length shouldBe 1000
////        case MqttPublishError(tenant, message) =>
////          assert(false)
////      }
//
//      (for {
//        e <- emailRepo.byTenant("myeeg")
//      } yield(e)).onComplete {
//        case Success(e) => println(e)
//      }
//
//
////      emailRepo.all().onComplete {
////        case Success(l) => l.length shouldBe 1000
////        case Failure(_) => assert(false)
////      }
//
////      Thread.sleep(2000)
////      testKit.stop(mailActor.ref)
//      Mailbox.clearAll()
//    }
//
//    "fetch email collection with unknown protocol" in {
//
//      //      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
//      val mailActor = spawn(TenantMailActor(tenantConfig, messageStore, emailRepo))
//      val replyActor = createTestProbe[MqttCommand]()
//
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]")
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_TES_ONL_01.00 MessageId=123456678]")
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]")
//
//      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)
//
//      val mqttMsg = replyActor.receiveMessage(5.minutes)
//
//      mqttMsg match {
//        case MqttPublish(mails) =>
//          mails.length shouldBe 2
//        case MqttPublishError(tenant, message) =>
//          assert(false)
//      }
//      Mailbox.clearAll()
//    }
//
//    "fetch email collection with wrong header" in {
//
//      //      val messageStoreProbe = createTestProbe[MessageStorage.Command[_]]()
//      val mailActor = spawn(TenantMailActor(tenantConfig, messageStore, emailRepo))
//      val replyActor = createTestProbe[MqttCommand]()
//
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]")
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "Wrong Header")
//      perpareEmail("myeeg", "ZUSTIMMUNG_ECON.xml", "[EC_REQ_ONL_01.00 MessageId=123456678]")
//
//      mailActor ! FetchEmailCommand("myeeg", "", replyActor.ref)
//
//      val mqttMsg = replyActor.receiveMessage(5.minutes)
//
//      mqttMsg match {
//        case MqttPublish(mails) =>
//          mails.length shouldBe 3
//          mails(1).message.message.messageCode shouldBe ERROR_MESSAGE
//          println(mails(1))
//        case MqttPublishError(tenant, message) =>
//          assert(false)
//      }
//      Mailbox.clearAll()
//    }
  }

}
