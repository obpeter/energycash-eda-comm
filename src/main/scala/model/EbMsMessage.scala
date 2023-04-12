package at.energydash
package model

import model.JsonImplicit._
import model.enums.EbMsMessageType
import model.enums.EbMsMessageType.EbMsMessageType
import model.enums.MeterDirectionType.MeterDirectionType

import java.util.Date

case class ResponseData(MeteringPoint: Option[String],
                        ResponseCode: Seq[BigInt])

case class Timeline(from: Date, to: Date)

case class Meter(meteringPoint: String, direction: Option[MeterDirectionType])

case class EnergyValue(from: Date, to: Option[Date], method: Option[String], value: BigDecimal)

case class EnergyData(meterCode: String, value: Seq[EnergyValue])

case class Energy(start: Date, end: Date, interval: String, nInterval: BigInt, data: Seq[EnergyData])

case class EbMsMessage(
                        messageId: Option[String] = None,
                        conversationId: String,
                        sender: String,
                        receiver: String,
                        messageCode: EbMsMessageType = EbMsMessageType.ENERGY_FILE_RESPONSE,
                        requestId: Option[String] = None,
                        meter: Option[Meter] = None,
                        ecId: Option[String] = None, // Community ID
                        responseData: Option[Seq[ResponseData]] = None,
                        energy: Option[Energy] = None,
                        timeline: Option[Timeline] = None,
                        meterList: Option[Seq[Meter]] = None,
                        errorMessage: Option[String] = None
                      )

object JsonImplicit {

  import io.circe.syntax._
  import io.circe.{Decoder, Encoder}

  implicit val dateTimeEncoder: Encoder[Date] = Encoder.instance(a => a.getTime.asJson)
  implicit val dateTimeDecoder: Decoder[Date] = Decoder.instance(a => a.as[Long].map(new Date(_)))
}