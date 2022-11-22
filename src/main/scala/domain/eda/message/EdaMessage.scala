package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.EbMsMessage

import scala.util.Try
import scala.xml.{Elem, NodeSeq}

trait EdaMessage[EDAType] {

  val message: EbMsMessage

  implicit val edaType: EDAType = edaType

  def toXML: NodeSeq
  def toByte: ByteString
}

trait EdaResponseType {
  def fromXML(xmlFile: Elem): Try[EdaMessage[_]]
}