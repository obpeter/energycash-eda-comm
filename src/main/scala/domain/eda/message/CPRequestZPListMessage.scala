package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.{EbMsMessage, ResponseData}
import at.energydash.model.enums.EbMsMessageType
import scalaxb.{DataRecord, Helper}
import xmlprotocol.{AddressType, CPRequest, DocumentMode, ECNumber, MarketParticipantDirectoryType2, Number01Value2, Number01u4612, ProcessDirectoryType2, RoutingAddress, RoutingHeader, SIMU, SchemaVersionType3}

import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, GregorianCalendar}
import javax.xml.datatype.{DatatypeConstants, DatatypeFactory}
import scala.util.Try
import scala.xml.{Elem, NodeSeq, XML}

case class CPRequestZPListMessage(message: EbMsMessage) extends EdaMessage[CPRequest] {

  def toXML: NodeSeq = {
    import java.util.GregorianCalendar
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val dateFmt = new SimpleDateFormat("yyyy-MM-dd")

    val doc = CPRequest(
      MarketParticipantDirectoryType2(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](SIMU)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType3](Number01u4612)),
        )
      ),
      ProcessDirectoryType2(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
        message.meter.map(x=>x.meteringPoint).getOrElse(""),
        message.timeline.map(t => {
            val from = new GregorianCalendar();from.setTime(t.from);from.set(Calendar.MILLISECOND, 0)
          from.clear(Calendar.SECOND); from.clear(Calendar.MINUTE); from.clear(Calendar.HOUR)
            val to = new GregorianCalendar();to.setTime(t.to);to.set(Calendar.MILLISECOND, 0)
          to.clear(Calendar.SECOND); to.clear(Calendar.MINUTE); to.clear(Calendar.HOUR)
          xmlprotocol.Extension(
              None,
              None,
              None,
              None,
              None,
              DateTimeFrom = Some(Helper.toCalendar(from)),
              DateTimeTo = Some(Helper.toCalendar(to)),
              None,
              None,
              false)
          }),

      )
    )
    scalaxb.toXML[CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12"), Some("CPRequest"),
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12",
        Some("cp2") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
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

object CPRequestZPListMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestZPListMessage] = {
    Try(scalaxb.fromXML[CPRequest](xmlFile)).map(document =>
      CPRequestZPListMessage(
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
        )
      )
    )
  }
}