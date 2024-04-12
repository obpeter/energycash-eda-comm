package at.energydash

import domain.eda.message._
import model.EbMsMessage
import model.enums.EbMsMessageType._

package object actor {
  def mergeEbmsMessage(stored: Option[EbMsMessage], current: EdaMessage): EdaMessage = {
    current.message.messageCode match {
      case ENERGY_SYNC_REJECTION | ENERGY_SYNC_RES =>
        CPRequestMeteringValue(current.message.copy(meter=stored.flatMap(_.meter), ecId = stored.flatMap(_.ecId)))
      case EDA_MSG_ABLEHNUNG_CCMS | EDA_MSG_ANTWORT_CCMS =>
        CMRevokeRequest(current.message.copy(consentEnd = stored.flatMap(_.consentEnd), ecId = stored.flatMap(_.ecId)))
      case CHANGE_METER_PARTITION_ANSWER | CHANGE_METER_PARTITION_REJECTION =>
        ECPartitionChangeMessage(current.message.copy(meterList = stored.flatMap(_.meterList), ecId = stored.flatMap(_.ecId)))
      case ONLINE_REG_ANSWER | ONLINE_REG_REJECTION | ONLINE_REG_APPROVAL | ONLINE_REG_COMPLETION =>
        CMRequestRegistrationOnline(current.message.copy(ecId = stored.flatMap(_.ecId)))
      case _ =>
        current
    }
  }
}
