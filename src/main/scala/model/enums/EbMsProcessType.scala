package at.energydash
package model.enums

import io.circe.{Decoder, Encoder}

object EbMsProcessType extends Enumeration {
    type EbMsProcessType = Value

    implicit val decoder: Decoder[Value] = Decoder.decodeEnumeration(this)
    implicit val encoder: Encoder[Value] = Encoder.encodeEnumeration(this)

    val PROCESS_ENERGY_RESPONSE: EbMsProcessType.Value = Value("CR_MSG")
    val PROCESS_ENERGY_RESPONSE_V0303: EbMsProcessType.Value = Value("CR_MSG_03.03")
    val PROCESS_REGISTER_ONLINE: EbMsProcessType.Value = Value("EC_REQ_ONL")
    val PROCESS_LIST_METERINGPOINTS: EbMsProcessType.Value = Value("EC_PODLIST")
    val PROCESS_METERINGPOINTS_VALUE: EbMsProcessType.Value = Value("CR_REQ_PT")
    val PROCESS_REVOKE_VALUE: EbMsProcessType.Value = Value("CM_REV_IMP")
    val PROCESS_REVOKE_CUS: EbMsProcessType.Value = Value("CM_REV_CUS")
    val PROCESS_UNKNOWN: EbMsProcessType.Value = Value("UNKNOWN")
//    val PROCESS_BASE_DATA: EbMsProcessType.Value = Value("EC_PODLIST")
}
