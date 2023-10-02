package at.energydash
package domain.eda.message

import model.enums.EbMsMessageType
import model._

import akka.util.ByteString
import scalaxb.Helper
import xmlprotocol.{CMRequest, ConsumptionRecord, ConsumptionRecord2, ConsumptionRecordVersion, DATEN_CRMSG, DocumentModeType, MarketParticipantDirectoryType2, Number01Value2, Number01u4630, PRODValue, ProcessDirectoryType2, RoutingAddress, RoutingHeader}

import java.io.StringWriter
import java.util.{Calendar, Date}
import scala.util.Try
import scala.xml.{Elem, Node, XML}

case class ConsumptionRecordMessage (message: EbMsMessage) extends EdaMessage[CMRequest] {
  override def toXML: Node = {
    import scalaxb.XMLStandardTypes._

    import java.util.GregorianCalendar

    val calendar: GregorianCalendar = new GregorianCalendar
    calendar.setTime(new Date)
    calendar.set(Calendar.MILLISECOND, 0)

    val processCalendar = new GregorianCalendar(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    processCalendar.add(Calendar.DAY_OF_MONTH, 3)

    val doc = ConsumptionRecord(
      MarketParticipantDirectoryType2(
        RoutingHeader(
          RoutingAddress(message.sender),
          RoutingAddress(message.receiver),
          Helper.toCalendar(calendar)
        ),
        Number01Value2,
        DATEN_CRMSG,
        Map(
          ("@DocumentMode", scalaxb.DataRecord[DocumentModeType](PRODValue)),
          ("@Duplicate", scalaxb.DataRecord(false)),
          ("@SchemaVersion", scalaxb.DataRecord[ConsumptionRecordVersion](Number01u4630)),
        )

      ),
      ProcessDirectoryType2(
        message.messageId.get,
        message.conversationId,
        Helper.toCalendar(calendar),
        message.meter.map(x => x.meteringPoint).get
      )
    )

    scalaxb.toXML[ConsumptionRecord](doc, Some("http://www.ebutilities.at/schemata/customerprocesses/consumptionrecord/01p30"), Some("ConsumptionRecord"),
      scalaxb.toScope(None -> "http://www.ebutilities.at/schemata/customerprocesses/common/types/01p20"), true).head
  }

  override def toByte: ByteString = {

    val xml = toXML

    val xmlString = new StringWriter()
    XML.write(xmlString, xml, "UTF-8", true, null)

    ByteString.fromString(xmlString.toString)
  }
}

object ConsumptionRecordMessage extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[ConsumptionRecordMessage] = {
    Try(scalaxb.fromXML[ConsumptionRecord](xmlFile)).map(document =>
      ConsumptionRecordMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          meter = Some(Meter(document.ProcessDirectory.MeteringPoint, None)),
          energy = Some(document.ProcessDirectory.Energy.map(energy => Energy(
            energy.MeteringPeriodStart.toGregorianCalendar.getTime,
            energy.MeteringPeriodEnd.toGregorianCalendar.getTime,
            energy.MeteringIntervall.toString,
            energy.NumberOfMeteringIntervall,
            data=energy.EnergyData.map(v =>
                EnergyData(
                  v.MeterCode,
                  v.EP.map(vv => EnergyValue(
                    vv.DTF.toGregorianCalendar.getTime,
                    vv.DTT.map(dtt=>dtt.toGregorianCalendar.getTime),
                    vv.MM.map(mm => mm.toString),
                    vv.BQ
                ))
              )
            )
          )).head
          ),
        )
      )
    )
  }
}

object ConsumptionRecordMessageV0303 extends EdaResponseType {
  def fromXML(xmlFile: Elem): Try[ConsumptionRecordMessage] = {
    Try(scalaxb.fromXML[ConsumptionRecord2](xmlFile)).map(document =>
      ConsumptionRecordMessage(
        EbMsMessage(
          messageId = Some(document.ProcessDirectory.MessageId),
          conversationId = document.ProcessDirectory.ConversationId,
          sender = document.MarketParticipantDirectory.RoutingHeader.Sender.MessageAddress,
          receiver = document.MarketParticipantDirectory.RoutingHeader.Receiver.MessageAddress,
          messageCode = EbMsMessageType.withName(document.MarketParticipantDirectory.MessageCode.toString),
          meter = Some(Meter(document.ProcessDirectory.MeteringPoint, None)),
          energy = Some(document.ProcessDirectory.Energy.map(energy => Energy(
            energy.MeteringPeriodStart.toGregorianCalendar.getTime,
            energy.MeteringPeriodEnd.toGregorianCalendar.getTime,
            energy.MeteringIntervall.toString,
            energy.NumberOfMeteringIntervall,
            data=energy.EnergyData.map(v =>
              EnergyData(
                v.MeterCode,
                v.EP.map(vv => EnergyValue(
                  vv.DTF.toGregorianCalendar.getTime,
                  Some(vv.DTT.toGregorianCalendar.getTime),
                  vv.MM.map(mm => mm.toString),
                  vv.BQ
                ))
              )
            )
          )).head
          ),
        )
      )
    )
  }
}