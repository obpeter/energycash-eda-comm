package at.energydash
package domain.eda.message

import model.EbMsMessage
import model.enums.EbMsMessageType

import akka.util.ByteString
import scalaxb.Helper
import xmlprotocol.{AddressType, CPRequest, DocumentMode, DocumentModeType, ECNumber, MarketParticipantDirectoryType2, MarketParticipantDirectoryType8, Number01Value2, Number01u4612, Number01u4612Value, ProcessDirectoryType2, ProcessDirectoryType8, RoutingAddress, RoutingHeader, SIMU, SIMUValue, SchemaVersionType3, SchemaVersionType7}

import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node, NodeSeq, TopScope, XML}

case class CPRequestBaseDataMessage(message: EbMsMessage) extends EdaMessage[CPRequest] {

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")

    val doc = CPRequest(
      MarketParticipantDirectoryType8(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](SIMUValue)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType7](Number01u4612Value)),
        )
      ),
      ProcessDirectoryType8(
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

  override def toByte: ByteString = {
    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object CPRequestBaseDataMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestBaseDataMessage] = {
    Try(scalaxb.fromXML[CPRequest](xmlFile)).map(document =>
      CPRequestBaseDataMessage(
        EbMsMessage(
          Some(document.ProcessDirectory.MessageId),
          document.ProcessDirectory.ConversationId,
          document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          None,
          None,
          None,
          None,
          None,
          None,
          None,
        )
      )
    )
  }
}