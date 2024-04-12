package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType

import scala.util.Try
import scala.xml.{Elem, Node, NodeSeq}

case class EdaErrorMessage(message: EbMsMessage) extends EdaMessage {

  override def getVersion(version: Option[String]): EdaXMLMessage[_] = EdaErrorXMLMessage(message)
}


case class EdaErrorXMLMessage(message: EbMsMessage) extends EdaXMLMessage[String] {
  override def toXML: Node = NodeSeq.Empty.head
}

object EdaErrorXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[EdaErrorMessage] = {
    Try(EdaErrorMessage(
      EbMsMessage(
        messageCode = EbMsMessageType.ERROR_MESSAGE,
        messageCodeVersion = Some("01.00"),
        conversationId = "1",
        messageId = None,
        sender = "",
        receiver = "",
        errorMessage = Some((xmlFile \ "ReasonText").text)
      ))
    )
  }
}

object EdaWrongVersionXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[EdaErrorMessage] = {
    Try(EdaErrorMessage(
      EbMsMessage(
        messageCode = EbMsMessageType.ERROR_MESSAGE,
        messageCodeVersion = Some("01.00"),
        conversationId = "1",
        messageId = None,
        sender = "",
        receiver = "",
        errorMessage = Some("Wrong Process Version")
      ))
    )
  }
}