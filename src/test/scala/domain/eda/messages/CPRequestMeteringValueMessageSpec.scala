package at.energydash
package domain.eda.messages

import domain.eda.message.CPRequestMeteringValueXMLMessage
import model.EbMsMessage

import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike


class CPRequestMeteringValueMessageSpec extends AnyWordSpecLike with Matchers {

  import model.JsonImplicit._

  "Request Meter Values" should {
    "build XML File" in {

      val jsonObjectStr =
        """{
          |  "messageId" : "RC100181202306071686177700000000007",
          |  "conversationId" : "RC100181202306071686177700000000008",
          |  "sender" : "RC100181",
          |  "receiver" : "AT003000",
          |  "messageCode" : "ANFORDERUNG_PT",
          |  "requestId" : "CHKWFJ5N",
          |  "meter" : {
          |    "meteringPoint" : "AT0030000000000000000000000381701",
          |    "direction" : null
          |  },
          |  "ecId" : null,
          |  "responseData" : null,
          |  "energy" : null,
          |  "timeline" : {
          |    "from" : 1679702400000,
          |    "to" : 1680219900000
          |  },
          |  "meterList" : null,
          |  "errorMessage" : null
          |}
          |""".stripMargin

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => CPRequestMeteringValueXMLMessage(m).toXML
      }

      (node \ "ProcessDirectory" \ "Extension" \ "DateTimeFrom").text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      println(node)
    }
  }

}
