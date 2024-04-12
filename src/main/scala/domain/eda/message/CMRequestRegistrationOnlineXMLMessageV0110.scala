package at.energydash
package domain.eda.message

import config.Config
import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AddressType, CMNotification, CMRequest, CMRequest2, CONSUMPTIONValue, CONSUMPTIONValue2, DocumentModeType, ECMPList, ECMPList2, ECNumber, GENERATIONValue, GENERATIONValue2, MarketParticipantDirectoryType7, MarketParticipantDirectoryType9, Number01Value4, Number01u4610, Number01u4620, PRODValue, ProcessDirectoryType7, ProcessDirectoryType9, ReqType, ReqTypeType, RoutingAddress, RoutingHeader, SIMUValue, SchemaVersionType7, SchemaVersionType9}

import java.util.{Calendar, Date}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node}
case class CMRequestRegistrationOnline(message: EbMsMessage) extends EdaMessage {
  override def getVersion(version: Option[String] = None): EdaXMLMessage[_] = message.messageCodeVersion match {
    case Some("02.00") => CMRequestRegistrationOnlineXMLMessageV0200(message)
    case _ => CMRequestRegistrationOnlineXMLMessageV0110(message)
  }
}

case class CMRequestRegistrationOnlineXMLMessageV0110(message: EbMsMessage) extends EdaXMLMessage[CMRequest2] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10 http://www.ebutilities.at/schemata/customerprocesses/EC_REQ_ONL/01.00/ANFORDERUNG_ECON")

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
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x => x.meteringPoint),
        message.requestId.get,
        None,
        ReqType(
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

case class CMRequestRegistrationOnlineXMLMessageV0200(message: EbMsMessage) extends EdaXMLMessage[CMRequest] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20 http://www.ebutilities.at/schemata/customerprocesses/EC_REQ_ONL/02.00/ANFORDERUNG_ECON")

  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = CMRequest2(
      MarketParticipantDirectoryType9(
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
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType9](Number01u4620)),
        )

      ),
      ProcessDirectoryType9(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x => x.meteringPoint),
        message.requestId.get,
        None,
        ReqTypeType(
          "EnergyCommunityRegistration",
          Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
          Some(Helper.toCalendar(dateFmt.format(new GregorianCalendar(2099, 12, 31).getTime))),
          None, //Some(QHValue),
          None, //Some(DValue2),
          message.ecId,
          ECPartFact=message.meter.map { m => m.partFact.getOrElse(100)},
          None, //Some(BigDecimal(0.0)),
          message.meter.map { m =>
            m.direction match {
              case Some(MeterDirectionType.CONSUMPTION) => CONSUMPTIONValue2
              case Some(MeterDirectionType.GENERATION) => GENERATIONValue2
              case None => CONSUMPTIONValue2
            }
          }
        )
      )
    )

    scalaxb.toXML[CMRequest2](doc, Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20"), rootNodeLabel,
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("ns2") -> "http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p20",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true).head
  }
}


object CMRequestRegistrationOnlineXMLMessageV0110 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnline] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.ONLINE_REG_COMPLETION => Try(scalaxb.fromXML[ECMPList](xmlFile)).map(document =>
          CMRequestRegistrationOnline(
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
          CMRequestRegistrationOnline(
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
        Try(CMRequestRegistrationOnline(
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

object CMRequestRegistrationOnlineXMLMessageV0200 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnline] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.ONLINE_REG_COMPLETION => Try(scalaxb.fromXML[ECMPList2](xmlFile)).map(document =>
          CMRequestRegistrationOnline(
            EbMsMessage(
              messageId = Some(document.ProcessDirectory.MessageId),
              conversationId = document.ProcessDirectory.ConversationId,
              sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
              messageCodeVersion = Some("02.00"),
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
          CMRequestRegistrationOnline(
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
        Try(CMRequestRegistrationOnline(
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