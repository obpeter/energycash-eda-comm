package at.energydash
package domain.eda.messages

import at.energydash.domain.eda.message.{CMRequestProcessMessage, CPRequestZPListMessage}
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
import scala.xml.{NamespaceBinding, TopScope}


class CommObjectsSpec extends AnyFlatSpec {

  import at.energydash.model.JsonImplicit._

  val xmlObj = GCRequestAP(
    MarketParticipantDirectoryType(
      RoutingHeader(
        RoutingAddress("AT003000"),
        RoutingAddress("AT003000"),
        Helper.toCalendar("2002-10-10T12:00:00-05:00")
      ),
      Number01Value2,
      ANFORDERUNG_AP,
    ),
    ProcessDirectoryType(
      "MessageId",
      "ConversationId",
      Helper.toCalendar("2002-10-10T12:00:00-05:00"),
      "MeteringPoint",
      GCRequestAP_EXT("ParticipantMeter", Some(BigDecimal(0.0))) :: Nil
    )
  )

//  "A CPRequest Communication Object" should "exists" in {
//      xmlObj.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress should equal ("AT003000")
//  }

  "it" should "compile to xml" in {
    val jsonObject =
      """{
        |"messageCode":"ANFORDERUNG_ECP",
        |"messageId":"rctest202210161905235386216409991",
        |"conversationId":"ectest202210161905235380027488852",
        |"sender":"rctest",
        |"receiver":"ectest",
        |"meter": {"meteringPoint":"communityId"},
        |"timeline": {"from": 1672915665000, "to": 1672915665000}
        |}""".stripMargin
    val message = decode[EbMsMessage](jsonObject)

    val obj = message match {
      case Right(m) => CPRequestZPListMessage(m)
    }
    println(obj.toByte.map(_.toChar).mkString)

    1 should equal (1)
  }

  "it" should "compile to CMRequest XML" in {
    val jsonObject =
      """{
        |"messageCode":"ANFORDERUNG_ECON",
        |"messageId":"rctest202210161905235386216409991",
        |"conversationId":"ectest202210161905235380027488852",
        |"sender":"rctest",
        |"receiver":"ectest",
        |"requestId": "IWRN74PW",
        |"ecId": "AT00300000000RC100181000000956509",
        |"meter": {"meteringPoint":"AT0030000000000000000000000012345", "direction": "CONSUMPTION"},
        |"timeline": {"from": 1672915665000, "to": 1672915665000}
        |}""".stripMargin
    val message = decode[EbMsMessage](jsonObject)

    val obj = message match {
      case Right(m) => CMRequestProcessMessage(m)
    }
    println(obj.toByte.map(_.toChar).mkString)

    1 should equal(1)
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
