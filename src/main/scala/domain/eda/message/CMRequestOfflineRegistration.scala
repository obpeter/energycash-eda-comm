package at.energydash
package domain.eda.message

import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AddressType, CMNotification, CMRequest, CMRequest2, CONSUMPTIONValue, DocumentModeType, ECMPList, ECNumber, GENERATIONValue, MarketParticipantDirectoryType7, Number01Value4, Number01u4610, PRODValue, ProcessDirectoryType7, ReqType, RoutingAddress, RoutingHeader, SchemaVersionType7}

import java.util.{Calendar, Date}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node}
case class CMRequestOfflineRegistration(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = CMRequestOfflineRegistrationXMLMessage(message)
}

case class CMRequestOfflineRegistrationXMLMessage(message: EbMsMessage) extends EdaXMLMessage[CMRequest2] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10 http://www.ebutilities.at/schemata/customerprocesses/EC_REQ_OFF/01.00/ANFORDERUNG_ECOF")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = CMRequest(
      MarketParticipantDirectoryType7(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value4,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](PRODValue)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType7](Number01u4610)),
        )

      ),
      ProcessDirectoryType7(
        MessageId=message.messageId.get,
        ConversationId=message.conversationId,
        ProcessDate=Helper.toCalendar(dateFmt.format(calendar.getTime)),
        MeteringPoint=message.meter.map(x => x.meteringPoint),
        CMRequestId=message.requestId.get,
        ConsentId=message.meter.map(m=>m.consentId.getOrElse("")),
        CMRequest=ReqType(
          ReqDatType = "EnergyCommunityRegistration",
          DateFrom = Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
          DateTo = Some(Helper.toCalendar(dateFmt.format(new GregorianCalendar(2099, 12, 31).getTime))),
          MeteringIntervall = None, //Some(QHValue),
          TransmissionCycle = None, //Some(DValue2),
          ECID = message.ecId,
          ECShare = None, //Some(BigDecimal(0.0)),
          EnergyDirection = message.meter.map { m =>
            m.direction match {
              case Some(MeterDirectionType.CONSUMPTION) => CONSUMPTIONValue
              case Some(MeterDirectionType.GENERATION) => GENERATIONValue
              case _ => CONSUMPTIONValue
            }
          }
        )
      )
    )

    scalaxb.toXML[CMRequest](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10"), rootNodeLabel,
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("ns2") -> "http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true).head
  }
}

object CMRequestOfflineRegistrationXMLMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestOfflineRegistration] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.OFFLINE_REG_COMPLETION => Try(scalaxb.fromXML[ECMPList](xmlFile)).map(document =>
          CMRequestOfflineRegistration(
            EbMsMessage(
              messageId = Some(document.ProcessDirectory.MessageId),
              conversationId = document.ProcessDirectory.ConversationId,
              sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
              messageCodeVersion = Some("01.00"),
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
        case _ => Try(scalaxb.fromXML[CMNotification](xmlFile)).map(document =>
          CMRequestOfflineRegistration(
            EbMsMessage(
              messageId=Some(document.ProcessDirectory.MessageId),
              conversationId=document.ProcessDirectory.ConversationId,
              sender=document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver=document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode=EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode),
              messageCodeVersion=Some("01.11"),
              requestId=Some(document.ProcessDirectory.CMRequestId),
              responseData=Some(document.ProcessDirectory.ResponseData.map(r => ResponseData(r.MeteringPoint, r.ResponseCode))),
            )
          )
        )
      }
      case Failure(exception) =>
        Try(CMRequestOfflineRegistration(
          EbMsMessage(
            messageCode = EbMsMessageType.ERROR_MESSAGE,
            messageCodeVersion=Some("01.00"),
            conversationId = "1",
            messageId = None,
            sender = "",
            receiver = "",
            errorMessage = Some(exception.getMessage)
          )
        ))
    }
  }
}
