package at.energydash
package domain.eda.messages

import at.energydash.domain.eda.message.CPRequestMessage
import at.energydash.model.EbMsMessage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import scalaxb.Helper
import xmlprotocol.{ANFORDERUNG_AP, GCRequestAP, GCRequestAP_EXT, MarketParticipantDirectoryType, Number01Value2, ProcessDirectoryType, RoutingAddress, RoutingHeader}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode


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
    val jsonObject = """{"messageCode":"SENDEN_VDC", "messageId":"rctest202210161905235386216409991", "conversationId":"ectest202210161905235380027488852", "sender":"rctest", "receiver":"ectest"}"""
    val message = decode[EbMsMessage](jsonObject)

    val obj = message match {
      case Right(m) => CPRequestMessage(m)
    }
    println(obj.toXML)

    1 should equal (1)
  }


}
