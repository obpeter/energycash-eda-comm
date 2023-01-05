package at.energydash
package model.enums

import io.circe.{Decoder, Encoder}

object EbMsMessageType extends Enumeration {
  type EbMsMessageType = Value

  implicit val decoder: Decoder[Value] = Decoder.decodeEnumeration(this)
  implicit val encoder: Encoder[Value] = Encoder.encodeEnumeration(this)

  val ENERGY_FILE_RESPONSE: EbMsMessageType.Value = Value("DATEN_CRMSG")
  val ONLINE_REG_ANSWER: EbMsMessageType.Value = Value("ANTWORT_ECON")
  val ONLINE_REG_INIT: EbMsMessageType.Value = Value("ANFORDERUNG_ECON")
  val ZP_LIST: EbMsMessageType.Value = Value("ANFORDERUNG_ECP")
}
