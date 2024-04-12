package at.energydash
package domain.eda.message

import config.Config
import model.enums.{EbMsMessageType, EcDisModelEnum, EcTypeEnum, MeterDirectionType}
import model.{EbMsMessage, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AddressType, CCValue, CONSUMPTIONValue3, CPNotification, DocumentModeType, ECMPList, ECMPList2, ECNumber, GCValue, GENERATIONValue3, MPListDataType, MPTimeDataType, MarketParticipantDirectoryType14, Number01Value4, Number01u4610Value, PRODValue, ProcessDirectoryType14, RC_LValue, RC_RValue, RoutingAddress, RoutingHeader, SIMUValue, SchemaVersionType11}

import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node}


case class ECPartitionChangeMessage(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = ECPartitionChangeXMLMessage(message)
}

case class ECPartitionChangeXMLMessage(message: EbMsMessage) extends EdaXMLMessage[ECMPList] {

  override def rootNodeLabel: Some[String] = Some("ECMPList")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10 " +
      "http://www.ebutilities.at/schemata/customerprocesses/EC_PRTFACT_CHANGE/01.00/ANFORDERUNG_CPF")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val now = new Date
    val processDate = Calendar.getInstance
    processDate.add(Calendar.DATE, 1)
//    val calendar: GregorianCalendar = new GregorianCalendar
//    calendar.setTime(new Date)
//    calendar.set(Calendar.MILLISECOND, 0)

    val doc = ECMPList2(
      MarketParticipantDirectory=MarketParticipantDirectoryType14(
        RoutingHeader=RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(MessageHelper.buildCalendar(now))
        ),
        Sector=Number01Value4,
        MessageCode="ANFORDERUNG_CPF",
        attributes=Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](Config.interfaceMode match {
            case "SIMU" => SIMUValue
            case _ => PRODValue
          })),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType11](Number01u4610Value)),
        )
      ),
      ProcessDirectory=ProcessDirectoryType14(
        MessageId = message.messageId.get,
        ConversationId = message.conversationId,
        ProcessDate = Helper.toCalendar(MessageHelper.buildCalendarDate(processDate.getTime)),
        ECID = message.ecId.get,
        ECType = message.ecType match {
          case Some(EcTypeEnum.GEA) => GCValue
          case Some(EcTypeEnum.REGIONAL) => RC_RValue
          case Some(EcTypeEnum.BEG) => CCValue
          case _ => RC_LValue
        },
        ECDisModel = message.ecDisModel match {
          case Some(EcDisModelEnum.STATIC) => xmlprotocol.SValue
          case _ => xmlprotocol.DValue11
        },
        MPListData = message.meterList.get.map(m =>
          MPListDataType(MeteringPoint = m.meteringPoint,
            MPTimeData = Seq(MPTimeDataType(
              DateFrom = Helper.toCalendar(MessageHelper.buildCalendarDate(now)),
              DateTo = Helper.toCalendar("2099-12-31"),
              EnergyDirection = m.direction match {
                case Some(MeterDirectionType.CONSUMPTION) => CONSUMPTIONValue3
                case _ => GENERATIONValue3
              },
              ECPartFact = m.partFact.get,
              DateActivate = Helper.toCalendar(MessageHelper.buildCalendarDate(m.activation.get)),
            ))))
      )
    )

    scalaxb.toXML[ECMPList2](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10"), rootNodeLabel,
      scalaxb.toScope(
        Some("rv") -> "http://www.ebutilities.at/schemata/customerprocesses/ecmplist/01p10",
        Some("ct") -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance",
      ),
      true).head
  }
}

object ECPartitionChangeXMLMessage extends EdaResponseType {
  override def fromXML(xmlFile: Elem): Try[ECPartitionChangeMessage] =
    Try(scalaxb.fromXML[CPNotification](xmlFile)).map(document =>
      ECPartitionChangeMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          messageCodeVersion = Some("01.20"),
          responseData = Some(document.ProcessDirectory.ResponseData.ResponseCode.map(r => ResponseData(None, List(r)))),
        )
      )
    )
}
