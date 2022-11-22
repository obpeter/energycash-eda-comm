package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.{EbMsMessage, ResponseData}
import at.energydash.model.enums.EbMsMessageType
import scalaxb.{DataRecord, Helper}
import xmlprotocol.{ANFORDERUNG_PT, CPRequest, DocumentMode, MarketParticipantDirectoryType2, Number01Value2, Number01u4612, ProcessDirectoryType2, RoutingAddress, RoutingHeader, SIMU, SchemaVersionType3}

import java.io.StringWriter
import java.util.{Calendar, Date, GregorianCalendar}
import javax.xml.datatype.DatatypeFactory
import scala.util.Try
import scala.xml.{Elem, NodeSeq, XML}

case class CPRequestMessage(message: EbMsMessage) extends EdaMessage[CPRequest] {

  def toXML: NodeSeq = {
    import java.util.GregorianCalendar
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = CPRequest(
      MarketParticipantDirectoryType2(
        RoutingHeader(
          RoutingAddress(message.sender),
          RoutingAddress(message.receiver),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        ANFORDERUNG_PT,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](SIMU)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType3](Number01u4612)),
        )
      ),
      ProcessDirectoryType2(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(processCalendar),
        message.meter.map(x=>x.meteringPoint).get,
      )
    )
    scalaxb.toXML[CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00"), Some("GCRequestAP"),
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("cp1") -> "http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true)
  }

  def toByte: ByteString = {
    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml.head, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object CPRequestMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestMessage] = {
    Try(scalaxb.fromXML[CPRequest](xmlFile)).map(document =>
      CPRequestMessage(
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
        )
      )
    )
  }
}