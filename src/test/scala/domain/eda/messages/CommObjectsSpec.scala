package at.energydash
package domain.eda.messages

import at.energydash.domain.eda.message.{CMRequestRegistrationOnlineMessage, CPRequestMeteringValueMessage, CPRequestZPListMessage}
import at.energydash.model.EbMsMessage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import scalaxb.Helper
import xmlprotocol.{ANFORDERUNG_AP, GCRequestAP, GCRequestAP_EXT, MarketParticipantDirectoryType, Number01Value2, ProcessDirectoryType, RoutingAddress, RoutingHeader}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.xml.NodeSeq.fromSeq
import scala.xml.{NamespaceBinding, TopScope}


class CommObjectsSpec extends AnyFlatSpec {

  import at.energydash.model.JsonImplicit._

  "it" should "compile to CP_LIST XML" in {
    val jsonObject =
      """{
        |"messageCode":"ANFORDERUNG_ECP",
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
      case Right(m) => CPRequestZPListMessage(m).toXML
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
      case Right(m) => CPRequestMeteringValueMessage(m)
    }

    node shouldBe a [CPRequestMeteringValueMessage]
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
