package at.energydash
package domain.eda.message

import model.enums.{EbMsMessageType, MeterDirectionType}
import model.{EbMsMessage, Meter, ResponseData}

import scalaxb.Helper
import xmlprotocol.{AddressType, CMNotification, CMRequest, CONSUMPTION, DocumentModeType, ECMPList, ECNumber, GENERATION, MarketParticipantDirectoryType8, Number01Value2, Number01u4610, PRODValue, ProcessDirectoryType8, ReqType, RoutingAddress, RoutingHeader, SchemaVersionType6}

import java.util.{Calendar, Date}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node}

case class CMRequestRegistrationOnlineMessageV0100(message: EbMsMessage) extends EdaMessage[CMRequest] {
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
      MarketParticipantDirectoryType8(
        RoutingHeader(
          RoutingAddress(message.sender, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          RoutingAddress(message.receiver, Map(("@AddressType", scalaxb.DataRecord[AddressType](ECNumber)))),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](PRODValue)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType6](Number01u4610)),
        )

      ),
      ProcessDirectoryType8(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x => x.meteringPoint),
        message.requestId.get,
        None,
        ReqType(
          "EnergyCommunityRegistration",
          Helper.toCalendar(dateFmt.format(processCalendar.getTime)),
          Some(Helper.toCalendar(dateFmt.format(new GregorianCalendar(2099, 12, 31).getTime))),
          None, //Some(QHValue),
          None, //Some(DValue2),
          message.ecId,
          None, //Some(BigDecimal(0.0)),
          message.meter.map { m =>
            m.direction match {
              case Some(MeterDirectionType.CONSUMPTION) => CONSUMPTION
              case Some(MeterDirectionType.GENERATION) => GENERATION
              case None => CONSUMPTION
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
  //  override def toByte: ByteString = {
  //    akka.util.ByteString(Source.fromFile("/home/petero/projects/energycash/xml/CMRequest-test-message.xml").mkString)
  //  }
}

object CMRequestRegistrationOnlineMessageV0100 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnlineMessageV0100] = {
    resolveMessageCode(xmlFile) match {
      case Success(mc) => mc match {
        case EbMsMessageType.ONLINE_REG_COMPLETION => Try(scalaxb.fromXML[ECMPList](xmlFile)).map(document =>
          CMRequestRegistrationOnlineMessageV0100(
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
        case _ => Try(scalaxb.fromXML[CMNotification](xmlFile)).map(document =>
          CMRequestRegistrationOnlineMessageV0100(
            EbMsMessage(
              messageId=Some(document.ProcessDirectory.MessageId),
              conversationId=document.ProcessDirectory.ConversationId,
              sender=document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
              receiver=document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
              messageCode=EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode),
              requestId=Some(document.ProcessDirectory.CMRequestId),
              responseData=Some(document.ProcessDirectory.ResponseData.map(r => ResponseData(r.MeteringPoint, r.ResponseCode))),
            )
          )
        )
      }
      case Failure(exception) =>
        Try(CMRequestRegistrationOnlineMessageV0100(
          EbMsMessage(
            messageCode = EbMsMessageType.ERROR_MESSAGE,
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