package at.energydash

import domain.eda.message.{CPRequestMeteringValueMessage, EdaMessage, CMRevokeRequest}
import model.EbMsMessage
import model.enums.EbMsMessageType._

package object actor {
  def mergeEbmsMessage(stored: Option[EbMsMessage], current: EdaMessage[_]): EdaMessage[_] = {
    current.message.messageCode match {
      case ENERGY_SYNC_REJECTION | ENERGY_SYNC_RES =>
        CPRequestMeteringValueMessage(current.message.copy(meter=stored.flatMap(_.meter)))
      case EDA_MSG_ABLEHNUNG_CCMS | EDA_MSG_ANTWORT_CCMS =>
        CMRevokeRequest(current.message.copy(consentEnd = stored.flatMap(_.consentEnd)))
      case _ =>
        current
    }
  }
}
