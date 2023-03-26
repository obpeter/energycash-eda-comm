package at.energydash
package domain.eda.messages

import akka.testkit.TestActor.NullMessage.sender
import at.energydash.domain.eda.message.CMRequestRegistrationOnlineMessage
import at.energydash.model.enums.EbMsMessageType.{EbMsMessageType, encoder}
import at.energydash.model.enums.{EbMsMessageType, MeterDirectionType}
import at.energydash.model.{EbMsMessage, Meter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import io.circe.generic.auto._
import io.circe.syntax._


class CRRequestRegistrationOnlineMessageSpec extends AnyWordSpecLike with Matchers {
  import at.energydash.model.JsonImplicit._

  "Registration Online Message" should {
    "build XML File" in {

      val testMessage = EbMsMessage(
        conversationId = "AT003000202303041506076450000003761",
        requestId = Some("5JWLV5Z3"),
        messageId = Some("RC100181202303041506080740000003762"),
        sender = "RC100130", receiver = "AT003000", messageCode = EbMsMessageType.ONLINE_REG_INIT,
        meter = Some(Meter("AT0030000000000000000000000655856", Some(MeterDirectionType.CONSUMPTION))), ecId = Some("AT00300000000RC100181000000956509"))

      val node = CMRequestRegistrationOnlineMessage(testMessage).toXML

      (node \ "MarketParticipantDirectory" \ "MessageCode").text shouldBe EbMsMessageType.ONLINE_REG_INIT.toString
      (node \ "ProcessDirectory" \ "MeteringPoint").text shouldBe "AT0030000000000000000000000655856"
      (node \ "ProcessDirectory" \ "CMRequest" \ "ECID").text shouldBe "AT00300000000RC100181000000956509"
      (node \ "ProcessDirectory" \ "CMRequest" \ "EnergyDirection").text shouldBe MeterDirectionType.CONSUMPTION.toString
    }
  }

}
