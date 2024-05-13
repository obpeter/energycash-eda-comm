package at.energydash
package domain.eda.message

import config.Config
import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AUFHEBUNG_CCMS, AddressType, CMNotification, CMRevoke, DocumentModeType, ECNumber, MarketParticipantDirectoryType6, Number01Value4, Number01u4600Value4, PRODValue, ProcessDirectoryType6, RoutingAddress, RoutingHeader, SIMUValue, SchemaVersionType6}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}
case class CMRevokeRequest(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CMRevokeRequestV0100(message)
}

case class CMRevokeRequestV0100(message: EbMsMessage) extends EdaXMLMessage[CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
  Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00 " +
    "http://www.ebutilities.at/schemata/customerprocesses/CM_REV_SP/01.02/AUFHEBUNG_CCMS")

  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processDate = Calendar.getInstance
    processDate.add(Calendar.DATE, 1)

    val doc = CMRevoke(
      MarketParticipantDirectoryType6(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value4,
        AUFHEBUNG_CCMS,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](Config.interfaceMode match {
            case "SIMU" => SIMUValue
            case _ => PRODValue
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType6](Number01u4600Value4)),
        )
      ),
      ProcessDirectoryType6(
        MessageId = message.messageId.get,
        ConversationId = message.conversationId,
        ConsentId = message.requestId.get,
        MeteringPoint = message.meter.map(x => x.meteringPoint).get,
        ConsentEnd = Helper.toCalendar(dateFmt.format(message.consentEnd.getOrElse(new Date).getTime)),
        Reason = message.reason,
      )
    )

    scalaxb.toXML[CMRevoke](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00"), rootNodeLabel,
      scalaxb.toScope(
        Some("rv") -> "http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
        ),
      true).head
  }
}

object CMRevokeRequestV0100 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeRequest] = {
    Try(scalaxb.fromXML[CMNotification](xmlFile)).map(document => {
      CMRevokeRequest(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.11"),
          responseData = Some(document.ProcessDirectory.ResponseData.map(r => ResponseData(r.MeteringPoint, r.ResponseCode)))
        )
      )
    }
    )
  }
}
