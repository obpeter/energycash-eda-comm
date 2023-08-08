package at.energydash

import domain.eda.message.{CPRequestMeteringValueMessage, EdaMessage}
import model.EbMsMessage
import model.enums.EbMsMessageType.{ENERGY_SYNC_REJECTION, ENERGY_SYNC_RES}

package object actor {
  def mergeEbmsMessage(stored: Option[EbMsMessage], current: EdaMessage[_]): EdaMessage[_] = {
    current.message.messageCode match {
      case ENERGY_SYNC_REJECTION | ENERGY_SYNC_RES =>
        CPRequestMeteringValueMessage(current.message.copy(meter=stored.flatMap(_.meter)))
      case _ => current
    }
  }
}
