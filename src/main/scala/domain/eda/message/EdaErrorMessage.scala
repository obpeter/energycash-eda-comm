package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType

import scala.util.Try
import scala.xml.{Elem, Node, NodeSeq}

case class EdaErrorMessage(message: EbMsMessage) extends EdaMessage[String] {
  override def toXML: Node = NodeSeq.Empty.head
}

object EdaErrorMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[EdaErrorMessage] = {
    Try(EdaErrorMessage(
      EbMsMessage(
        messageCode = EbMsMessageType.ERROR_MESSAGE,
        conversationId = "1",
        messageId = None,
        sender = "",
        receiver = "",
        errorMessage = Some((xmlFile \ "ReasonText").text)
      ))
    )
  }
}

