package at.energydash
package domain.eda.message

import model.enums.EbMsMessageType
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AUFHEBUNG_CCMI, AddressType, CMRevoke, DocumentModeType, ECNumber, MarketParticipantDirectoryType6, Number01Value4, Number01u4600Value3, PRODValue, ProcessDirectoryType6, RoutingAddress, RoutingHeader, SchemaVersionType5}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}

case class CMRevokeMessageV0100(message: EbMsMessage) extends EdaMessage[CMRevoke] {
  override def rootNodeLabel: Some[String] = Some("CMRevoke")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrevoke/01p00 http://www.ebutilities.at/schemata/customerprocesses/CM_REV_IMP/01.00/AUFHEBUNG_CCMI")


  def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val doc = CMRevoke(
      MarketParticipantDirectoryType6(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value4,
        AUFHEBUNG_CCMI,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](PRODValue)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType5](Number01u4600Value3)),
        )
      ),
      ProcessDirectoryType6(
        message.messageId.get,
        message.conversationId,
        message.requestId.get,
        message.meter.map(x => x.meteringPoint).get,
        Helper.toCalendar(dateFmt.format(message.consentEnd.getOrElse(new Date).getTime)),
        message.reason,
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

object CMRevokeMessageV0100 extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[CMRevokeMessageV0100] = {
    Try(scalaxb.fromXML[CMRevoke](xmlFile)).map(document => {
      CMRevokeMessageV0100(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          responseData = Some(List(ResponseData(
            Some(document.ProcessDirectory.MeteringPoint),
            List(1099),
            Some(document.ProcessDirectory.ConsentEnd.toGregorianCalendar().getTime.getTime))))
        )
      )
    }
    )
  }
}