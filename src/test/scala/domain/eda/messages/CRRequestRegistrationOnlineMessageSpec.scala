package at.energydash
package domain.eda.messages

import domain.eda.message.{CMRequestRegistrationOnlineXMLMessageV0110, CMRequestRegistrationOnlineXMLMessageV0200}
import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter}

import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.io.Source
import scala.util.{Failure, Success}


class CRRequestRegistrationOnlineMessageSpec extends AnyWordSpecLike with Matchers {
  import model.JsonImplicit._

  "Registration Online Message" should {
    "build XML File" in {

      val testMessage = EbMsMessage(
        conversationId = "AT003000202303041506076450000003761",
        requestId = Some("5JWLV5Z3"),
        messageId = Some("RC100181202303041506080740000003762"),
        sender = "RC100130", receiver = "AT003000", messageCode = EbMsMessageType.ONLINE_REG_INIT, messageCodeVersion=Some("02.00"),
        meter = Some(Meter("AT0030000000000000000000000655856", Some(MeterDirectionType.CONSUMPTION))), ecId = Some("AT00300000000RC100181000000956509"))

      val node = CMRequestRegistrationOnlineXMLMessageV0200(testMessage).toXML

      (node \ "MarketParticipantDirectory" \ "MessageCode").text shouldBe EbMsMessageType.ONLINE_REG_INIT.toString
      (node \ "ProcessDirectory" \ "MeteringPoint").text shouldBe "AT0030000000000000000000000655856"
      (node \ "ProcessDirectory" \ "CMRequest" \ "ECID").text shouldBe "AT00300000000RC100181000000956509"
      (node \ "ProcessDirectory" \ "CMRequest" \ "EnergyDirection").text shouldBe MeterDirectionType.CONSUMPTION.toString
    }
  }

  "Energy XML File" should {
    "Parse from ABSCHLUSS-ECON XML" in {
      //      val xmlFile = scala.xml.XML.load(new FileInputStream("/home/petero/projects/energycash/xml/DATEN_CRMSG/message-daten_crmsg.xml"))
      val xmlFile = scala.xml.XML.load(Source.fromResource("message-abschluss-econ.xml").reader())
      CMRequestRegistrationOnlineXMLMessageV0110.fromXML(xmlFile) match {
        case Success(m) =>
          m.asJson.deepDropNullValues.noSpaces.toString()
          assert(true)
        case Failure(exception) =>
          exception.toString
          fail(exception)
      }
    }
  }
}
