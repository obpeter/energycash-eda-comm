package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.{EbMsMessage, ResponseData}
import at.energydash.model.enums.{EbMsMessageType, MeterDirectionType}
import scalaxb.{Helper, `package`}
import scalaxb.`package`.toXML
import xmlprotocol.{AddressType, CMNotification, CMRequest, CONSUMPTION, DValue2, DocumentMode, DocumentModeType, ECNumber, GENERATION, MarketParticipantDirectoryType4, MarketParticipantDirectoryType6, Number01Value2, Number01u4610, ProcessDirectoryType4, ProcessDirectoryType6, QHValue, ReqType, RoutingAddress, RoutingHeader, SIMU, SIMUValue, SchemaVersionType5}

import java.io.StringWriter
import java.util.{Calendar, Date}
import scala.io.Source
import scala.util.Try
import scala.xml.{Elem, Node, XML}

case class CMRequestRegistrationOnlineMessage(message: EbMsMessage) extends EdaMessage[CMRequest] {
  override def rootNodeLabel: Option[String] = Some("CMRequest")

  override def schemaLocation: Option[String] =
    Some("http://www.ebutilities.at/schemata/customerconsent/cmrequest/01p10 http://www.ebutilities.at/schemata/customerprocesses/CM_REQ_ONL/01.10/ANFORDERUNG_CCMO")

  override def toXML: Node = {
    import java.util.GregorianCalendar
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = CMRequest(
      MarketParticipantDirectoryType6(
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
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType5](Number01u4610)),
        )

      ),
      ProcessDirectoryType6(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(dateFmt.format(calendar.getTime)),
        message.meter.map(x=>x.meteringPoint),
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

object CMRequestRegistrationOnlineMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestRegistrationOnlineMessage] = {
    Try(scalaxb.fromXML[CMNotification](xmlFile)).map(document =>
      CMRequestRegistrationOnlineMessage(
        EbMsMessage(
          Some(document.ProcessDirectory.MessageId),
          document.ProcessDirectory.ConversationId,
          document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode),
          Some(document.ProcessDirectory.CMRequestId),
          None,
          None,
          Some(document.ProcessDirectory.ResponseData.map(r => ResponseData(r.MeteringPoint, r.ResponseCode))),
          None,
          None,
          None,
        )
      )
    )
  }
}