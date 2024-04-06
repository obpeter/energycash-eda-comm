package at.energydash
package domain.eda.message

import config.Config
import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter}

import scalaxb.Helper
import xmlprotocol.{AddressType, CPRequest, DocumentModeType, ECMPList, ECNumber, MarketParticipantDirectoryType4, Number01Value4, Number01u4612, PRODValue, ProcessDirectoryType4, RoutingAddress, RoutingHeader, SIMUValue, SchemaVersionType4}

import java.text.SimpleDateFormat
import java.util.{Calendar, Date, TimeZone}
import scala.util.Try
import scala.xml.{Elem, NamespaceBinding, Node, TopScope}

case class CPRequestZPList(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CPRequestZPListXMLMessage(message)
}

case class CPRequestZPListXMLMessage(message: EbMsMessage) extends EdaXMLMessage[CPRequest] {

  override def rootNodeLabel: Some[String] = Some("CPRequest")

  override def schemaLocation: Option[String] = Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 " +
    "http://www.ebutilities.at/schemata/customerprocesses/EC_PODLIST/01.10/ANFORDERUNG_ECP")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val tz = TimeZone.getTimeZone("Europe/Vienna")
    val calendar: GregorianCalendar = new GregorianCalendar(tz)
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
//    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

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
        message.timeline.map(t => {
          val from = new GregorianCalendar(tz);from.setTime(t.from);from.set(Calendar.MILLISECOND, 0)
          from.clear(Calendar.SECOND); from.clear(Calendar.MINUTE); from.clear(Calendar.HOUR)
          val to = new GregorianCalendar(tz);to.setTime(t.to);to.set(Calendar.MILLISECOND, 0)
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

object CPRequestZPListXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestZPList] = {
    Try(scalaxb.fromXML[ECMPList](xmlFile)).map(document =>
      CPRequestZPList(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("04.10"),
          meterList = Some(document.ProcessDirectory.MPListData
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