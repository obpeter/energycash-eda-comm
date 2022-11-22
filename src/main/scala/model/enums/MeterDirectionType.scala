package at.energydash
package model.enums

import io.circe.{Decoder, Encoder}

object MeterDirectionType extends Enumeration {
  type MeterDirectionType = Value

  implicit val decoder: Decoder[Value] = Decoder.decodeEnumeration(this)
  implicit val encoder: Encoder[Value] = Encoder.encodeEnumeration(this)

  val CONSUMPTION: MeterDirectionType.Value = Value("CONSUMPTION")
  val GENERATION: MeterDirectionType.Value = Value("GENERATION")
}