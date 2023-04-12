package at.energydash
package domain.eda.message

import model.{EbMsMessage, Meter}
import model.enums.{EbMsMessageType, MeterDirectionType}
import scalaxb.{DataRecord, Helper}
import xmlprotocol.{AddressType, CPRequest, DocumentMode, DocumentModeType, ECMPList, ECNumber, MarketParticipantDirectoryType8, Number01Value2, Number01u4612Value, ProcessDirectoryType8, RoutingAddress, RoutingHeader, SIMU, SIMUValue, SchemaVersionType7}

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, NamespaceBinding, Node, TopScope}

case class CPRequestZPListMessage(message: EbMsMessage) extends EdaMessage[CPRequest] {

  override def rootNodeLabel: Some[String] = Some("CPRequest")

  override def schemaLocation: Option[String] = Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 " +
    "http://www.ebutilities.at/schemata/customerprocesses/EC_PODLIST/01.00/ANFORDERUNG_ECP")

  def toXML: Node = {
    import java.util.GregorianCalendar
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
//    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

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

//    scalaxb.toXML[CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12"), Some("CPRequest"),
//          scalaxb.toScope(
//            Some("cp") -> "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12",
//            Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
//          ),
//    //      TopScope,
//          false).head

    scalaxb.toXML[CPRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12"), rootNodeLabel,
      scalaxb.toScope(
        Some("cp") -> "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      true).head
  }

  private def defineNamespaceBinding(): NamespaceBinding = {
    val nsb2 = NamespaceBinding("schemaLocation", "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12/CPRequest_01p12.xsd", TopScope)
    val nsb3 = NamespaceBinding("xsi", "http://www.w3.org/2001/XMLSchema-instance", nsb2)
    val nsb4 = NamespaceBinding("cp", "http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12", TopScope)
//    val p = PrefixedAttribute("xsi", )
    NamespaceBinding(null, "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20", nsb2)
  }
}

object CPRequestZPListMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestZPListMessage] = {
    Try(scalaxb.fromXML[ECMPList](xmlFile)).map(document =>
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
          Some(document.ProcessDirectory.MPListData
            .map(mp =>
              Meter(
                mp.MeteringPoint,
                Some(MeterDirectionType.withName(mp.MPTimeData.head.EnergyDirection.toString))
              )
            )
          ),
        )
      )
    )
  }
}