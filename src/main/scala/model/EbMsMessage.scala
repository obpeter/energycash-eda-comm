package at.energydash
package model

import at.energydash.model.enums.MeterDirectionType.MeterDirectionType
import at.energydash.model.enums.EbMsMessageType.EbMsMessageType

import java.util.Date
import JsonImplicit._

case class ResponseData(MeteringPoint: Option[String],
                        ResponseCode: Seq[BigInt])

case class Meter(meteringPoint: String, direction: Option[MeterDirectionType])

case class EnergyValue(from: Date, to: Option[Date], method: Option[String], value: BigDecimal)
case class EnergyData(meterCode: String, value: Seq[EnergyValue])
case class Energy(start: Date, end: Date, interval: String, nInterval: BigInt, data: Seq[EnergyData])

case class EbMsMessage(
                        messageId: Option[String],
                        conversationId: String,
                        sender: String,
                        receiver: String,
                        messageCode: EbMsMessageType,
                        requestId: Option[String],
                        meter: Option[Meter],
                        ecId: Option[String],                   // Community ID
                        responseData: Option[Seq[ResponseData]],
                        energy: Option[Energy],
                     )

object JsonImplicit {
  import io.circe.{Encoder, Decoder}
  import io.circe.syntax._

  implicit val dateTimeEncoder: Encoder[Date] = Encoder.instance(a => a.getTime.asJson)
  implicit val dateTimeDecoder: Decoder[Date] = Decoder.instance(a => a.as[Long].map(new Date(_)))
}