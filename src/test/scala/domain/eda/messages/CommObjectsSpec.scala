package at.energydash
package domain.eda.messages

import domain.eda.message.{CPRequestMeteringValue, CPRequestMeteringValueXMLMessage, CPRequestZPList}
import model.EbMsMessage

import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

import scala.xml.{NamespaceBinding, TopScope}


class CommObjectsSpec extends AnyFlatSpec {

  import model.JsonImplicit._

  "it" should "compile to CP_LIST XML" in {
    val jsonObject =
      """{
        |"messageCode":"ANFORDERUNG_ECP",
        |"messageCodeVersion": "01.00",
        |"messageId":"rctest202210161905235386216409991",
        |"conversationId":"ectest202210161905235380027488852",
        |"sender":"rctest",
        |"receiver":"ectest",
        |"requestId": "IWRN74PW",
        |"timeline":{"from":1678402800000,"to":1678489200000},
        |"meter":{"meteringPoint":"AT00300000000RC100130000000952832"}
        |}""".stripMargin
    val message = decode[EbMsMessage](jsonObject)

    val node = message match {
      case Right(m) => CPRequestZPList(m).getVersion().toXML
    }

    (node \ "ProcessDirectory" \ "MeteringPoint").text shouldBe "AT00300000000RC100130000000952832"
  }

  "it" should "compile to CP_REQ_PT XML" in {
    val jsonObject ="""{
        |"messageId" : "RC100130202303131678741400000000001",
        |"conversationId" : "RC100130202303131678741400000000002",
        |"sender" : "RC100130",
        |"receiver" : "AT003000",
        |"messageCode" : "ANFORDERUNG_PT",
        |"messageCodeVersion": "01.00",
        |"requestId" : "A6ETEPER",
        |"meter" : {
        |  "meteringPoint" : "AT0030000000000000000000000670809",
        |  "direction" : null
        |},
        |"ecId" : null,
        |"responseData" : null,
        |"energy" : null,
        |"timeline" : {
        |  "from" : 1678402800000,
        |  "to" : 1678489200000
        |},
        |"meterList" : null
        |}""".stripMargin

    val message = decode[EbMsMessage](jsonObject)

    val node = message match {
      case Right(m) => CPRequestMeteringValue(m).getVersion()
    }

    node shouldBe a [CPRequestMeteringValueXMLMessage]
    (node.toXML \ "ProcessDirectory" \ "MeteringPoint").text shouldBe "AT0030000000000000000000000670809"
  }

  "it" should "compile to Namespace" in {
    val nsb2 = NamespaceBinding("schemaLocation", "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12/CPRequest_01p12.xsd", TopScope)
    val nsb3 = NamespaceBinding("xsi", "http://www.w3.org/2001/XMLSchema-instance", null)
    val nsb4 = NamespaceBinding("cp", "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12", TopScope)
    //    val p = PrefixedAttribute("xsi", )
    val n = NamespaceBinding(null, "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20", null)

    println(n.toString())
  }
}
