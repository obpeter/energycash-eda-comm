package at.energydash
package domain.eda.message

import akka.util.ByteString
import at.energydash.model.{EbMsMessage, ResponseData}
import at.energydash.model.enums.{EbMsMessageType, MeterDirectionType}
import scalaxb.{Helper, `package`}
import scalaxb.`package`.toXML
import xmlprotocol.{CMNotification, CMRequest, CONSUMPTION, DValue2, DocumentMode, GENERATION, MarketParticipantDirectoryType4, Number01Value2, Number01u4610, ProcessDirectoryType4, QHValue, ReqType, RoutingAddress, RoutingHeader, SIMU, SchemaVersionType5}

import java.io.StringWriter
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, NodeSeq, XML}

case class CMRequestProcessMessage(message: EbMsMessage) extends EdaMessage[CMRequest] {
  override def toXML: NodeSeq = {
    import java.util.GregorianCalendar
    import scalaxb.XMLStandardTypes._

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = CMRequest(
      MarketParticipantDirectoryType4(
        RoutingHeader(
          RoutingAddress(message.sender),
          RoutingAddress(message.receiver),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        message.messageCode.toString,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentMode](SIMU)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[SchemaVersionType5](Number01u4610)),
        )

      ),
      ProcessDirectoryType4(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(calendar),
        message.meter.map(x=>x.meteringPoint),
        message.requestId.get,
        None,
        ReqType(
          "EnergyCommunityRegistration",
          Helper.toCalendar(processCalendar),
          Some(Helper.toCalendar(new GregorianCalendar(2099, 12, 31))),
          Some(QHValue),
          Some(DValue2),
          message.ecId,
          Some(BigDecimal(0.0)),
          message.meter.map {
            case MeterDirectionType.CONSUMPTION => CONSUMPTION
            case MeterDirectionType.GENERATION => GENERATION
          }
        )
      )
    )

    scalaxb.toXML[CMRequest](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00"), Some("GCRequestAP"),
      scalaxb.toScope(
        None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20",
        Some("cp1") -> "http://www.ebutilities.at/schemata/customerprocesses/gc/gcrequestap/01p00",
        Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"),
      true)
  }

  override def toByte: ByteString = {
    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml.head, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object CMRequestProcessMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[CMRequestProcessMessage] = {
    Try(scalaxb.fromXML[CMNotification](xmlFile)).map(document =>
      CMRequestProcessMessage(
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
        )
      )
    )
  }
}