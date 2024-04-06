package at.energydash
package domain.eda.messages

import domain.eda.message.ECPartitionChangeXMLMessage
import model.EbMsMessage
import model.enums.EbMsMessageType

import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Date

class ECPartitionChangeSpec  extends AnyWordSpec with Matchers {
  import model.JsonImplicit._

  "Change Message XML File" should {
    "Parse to XML" in {
      val jsonObjectStr =
        """{
          | "messageId": "1234567890",
          | "conversationId":"",
          | "sender":"sepp.gaug",
          | "receiver":"obermueller.peter",
          | "messageCode":"ANFORDERUNG_CPF",
          | "messageCodeVersion":"",
          | "ecId":"AT00300000000RC100130000000952832",
          | "ecType":"LOCAL",
          | "ecDisModel":"DYNAMIC",
          | "meterList":[{
          |   "meteringPoint":"AT0030000000000000000000030044503",
          |   "direction":"GENERATION",
          |   "activation":1711138686617,
          |   "partFact":12
          | }]
          |}
          |""".stripMargin

      val tm = EbMsMessage(
        conversationId = "conversation",
        sender = "sender",
        receiver = "receiver",
        messageCode = EbMsMessageType.ENERGY_FILE_RESPONSE,
        energy = Some(model.Energy(
          start=new Date(),
          end=new Date(),
          interval="inteval",
          nInterval=BigInt(1),
          data=Seq(model.EnergyData(meterCode="meterCode", value=Seq(model.EnergyValue(from = new Date(), value = BigDecimal(1))))))),
        meterList = Some(Seq(
          model.Meter(
            meteringPoint = "meteringPoint",
            direction = Some(model.enums.MeterDirectionType.CONSUMPTION),
            activation = Some(new Date()), partFact = Some(BigDecimal(12))))))

      println(tm.asJson.toString())

      val message = decode[EbMsMessage](jsonObjectStr)

      val node = message match {
        case Right(m) => ECPartitionChangeXMLMessage(m).toXML
      }

      //      (node \ "ProcessDirectory" \ "ConsentEnd" ).text should fullyMatch regex """[12][0-9]{3}-[01][0-9]-[0-3][0-9]T[012][0-9]:[0-5][0-9]:00[\+]0[12]:00"""
      (node \ "ProcessDirectory" \ "ECID" ).text shouldBe "AT00300000000RC100130000000952832"
      (node \\ "MessageCode").text shouldBe "ANFORDERUNG_CPF"
      println(node)
    }
  }

}
