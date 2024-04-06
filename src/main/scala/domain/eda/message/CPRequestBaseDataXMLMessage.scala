package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType
import config.Config

import akka.util.ByteString
import scalaxb.Helper
import xmlprotocol.{AddressType, CPRequest, DocumentModeType, ECNumber, MarketParticipantDirectoryType4, Number01Value4, Number01u4612, PRODValue, ProcessDirectoryType4, RoutingAddress, RoutingHeader, SIMUValue, SchemaVersionType4}

import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node, XML}

case class CPRequestBaseData(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CPRequestBaseDataXMLMessage(message)
}

case class CPRequestBaseDataXMLMessage(message: EbMsMessage) extends EdaXMLMessage[CPRequest] {

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")

    val doc = CPRequest(
      MarketParticipantDirectoryType4(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value4,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](Config.interfaceMode match {
            case "SIMU" => SIMUValue
            case _ => PRODValue
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType4](Number01u4612)),
        )
      ),
      ProcessDirectoryType4(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
        message.meter.map(x=>x.meteringPoint).getOrElse(""),
        Some(xmlprotocol.Extension(None, None, None, None, None, None, None, None, None, AssumptionOfCosts = false)),
      )
    )

    rewriteRootSchema(scalaxb.toXML[CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12"), Some("CPRequest"),
      scalaxb.toScope(
        Some("cp") -> "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      false).head, "CPRequest",
      "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 CPRequest_01p12.xsd")

  }

  override def toByte: Try[ByteString] = Try {
    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object CPRequestBaseDataXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestBaseData] = {
    Try(scalaxb.fromXML[CPRequest](xmlFile)).map(document =>
      CPRequestBaseData(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.00"),
        )
      )
    )
  }
}