package at.energydash
package domain.eda.messages

import domain.eda.message.{CMRevokeRequestV0100, CMRevokeXMLMessageV0100}
import model.EbMsMessage
//import model.enums.EbMsProcessType._
import model.enums.EbMsMessageType

import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success
import scala.xml.Elem

class CMRevokeMessageSpec extends AnyWordSpec with Matchers {

  import model.JsonImplicit._

  "Revoke Message" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMI",
          |  "messageCodeVersion": "01.01",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRevokeXMLMessageV0100(m).toXML
      }

//      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ConsentEnd" ).text shouldBe  "2023-03-31"
      println(node)

      val obj = CMRevokeXMLMessageV0100.fromXML(node.asInstanceOf[Elem])
      obj match {
        case Success(o) =>
          o.message.messageCodeVersion shouldBe Some("01.01")
          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_AUFHEBUNG_CCMI
          o.message.sender shouldBe "TE000001"
          o.message.responseData.get.head.ConsentEnd shouldBe Some(1680213600000L)
          o.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000000101")
      }
    }
  }

  "Revoke Message CCMS" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMS",
          |  "messageCodeVersion": "01.01",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000000101",
          |    "direction" : null
          |  },
          |  "consentEnd": 1680219900000
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CMRevokeRequestV0100(m).toXML
      }

      //      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ConsentEnd" ).text shouldBe  "2023-03-31"
      println(node)

//      val obj = CMRevokeRequestV0100.fromXML(node.asInstanceOf[Elem])
//      obj match {
//        case Success(o) =>
//          o.message.messageCodeVersion shouldBe Some("01.01")
//          o.message.messageCode shouldBe EbMsMessageType.EDA_MSG_ANTWORT_CCMS
//          o.message.sender shouldBe "TE000001"
//          o.message.responseData.get.head.ConsentEnd shouldBe Some(1680213600000L)
//          o.message.responseData.get.head.MeteringPoint shouldBe Some("AT0030000000000000000000000000101")
//      }
    }
  }
}
