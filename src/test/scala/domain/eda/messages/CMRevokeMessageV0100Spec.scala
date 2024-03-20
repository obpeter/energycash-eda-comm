package at.energydash
package domain.eda.messages

import domain.eda.message.CMRevokeMessageV0100
import model.EbMsMessage

import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CMRevokeMessageV0100Spec extends AnyWordSpec with Matchers {

  import model.JsonImplicit._

  "Revoke Message" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "TE100001202306071686177700000000008",
          |  "sender" : "TE000001",
          |  "receiver" : "AT009999",
          |  "messageCode" : "AUFHEBUNG_CCMC",
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
        case Right(m) => CMRevokeMessageV0100(m).toXML
      }

//      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ConsentEnd" ).text shouldBe  "2023-03-31"
      println(node)
    }
  }
}
