package at.energydash
package domain.eda.message

import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}
import config.Config

import scalaxb.Helper
import xmlprotocol.{AddressType, CPNotification, CPRequest, DocumentModeType2, ECNumber, MarketParticipantDirectoryType4, Number01Value4, Number01u4612, PRODValue2, ProcessDirectoryType4, RoutingAddress, RoutingHeader, SIMUValue2, SchemaVersionType4}

import java.text.SimpleDateFormat
import java.util.{Calendar, Date, TimeZone}
import scala.util.Try
import scala.xml.{Elem, NamespaceBinding, Node, TopScope}

case class CPRequestMeteringValue(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CPRequestMeteringValueXMLMessage(message)
}

case class CPRequestMeteringValueXMLMessage(message: EbMsMessage) extends EdaXMLMessage[CPRequest] {

  override def rootNodeLabel: Some[String] = Some("CPRequest")

  override def schemaLocation: Option[String] = Some("http://www.ebutilities.at/schemata/customerprocesses/cprequest/01p12 " +
    "http://www.ebutilities.at/schemata/customerprocesses/CR_REQ_PT/03.00/ANFORDERUNG_PT")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
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
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType2](Config.interfaceMode match {
            case "SIMU" => SIMUValue2
            case _ => PRODValue2
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
          val tz = TimeZone.getTimeZone("Europe/Vienna")
          val from = new GregorianCalendar(tz);from.setTime(t.from);from.set(Calendar.MILLISECOND, 0)
          val to = new GregorianCalendar(tz);to.setTime(t.to);to.set(Calendar.MILLISECOND, 0)
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

object CPRequestMeteringValueXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CPRequestMeteringValue] = {
    Try(scalaxb.fromXML[CPNotification](xmlFile)).map(document =>
      CPRequestMeteringValue(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.13"),
          responseData = Some(document.ProcessDirectory.ResponseData.ResponseCode.map(r => ResponseData(None, List(r)))),
        )
      )
    )
  }
}