package at.energydash
package domain.eda.messages

import domain.eda.message.{CMRequestRegistrationOnlineXMLMessageV0110, CMRequestRegistrationOnlineXMLMessageV0200}
import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter}

import io.circe.generic.auto._
import io.circe.parser.decode
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
        sender = "RC100130", receiver = "AT003000", messageCode = EbMsMessageType.ONLINE_REG_INIT, messageCodeVersion = Some("02.00"),
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

    "BUG: Hofmann-PartFact" in {

      val jsonObjectStr = """ {
                       |                "consentEnd": null,
                       |                "conversationId": "RC100590202404251714030620000012569",
                       |                "ecDisModel": null,
                       |                "ecId": "AT00200000000RC100590000000000256",
                       |                "ecType": null,
                       |                "energy": null,
                       |                "errorMessage": null,
                       |                "messageCode": "ANFORDERUNG_ECON",
                       |                "messageCodeVersion": "02.00",
                       |                "messageId": "RC100590202404251714030620000012568",
                       |                "meter": {
                       |                    "activation": null,
                       |                    "direction": "GENERATION",
                       |                    "from": null,
                       |                    "meteringPoint": "AT0020000000000000000000020901971",
                       |                    "partFact": 90,
                       |                    "plantCategory": null,
                       |                    "share": null,
                       |                    "to": null
                       |                },
                       |                "meterList": null,
                       |                "reason": null,
                       |                "receiver": "AT002000",
                       |                "requestId": "6NEGUJJ5",
                       |                "responseData": null,
                       |                "sender": "RC100590",
                       |                "timeline": null
                       |        }""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRequestRegistrationOnlineXMLMessageV0200(m).toXML
      }
//      (node \ "ProcessDirectory" \ "Extension" \ "DateTimeFrom").text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      println(node)

    }
  }
}
