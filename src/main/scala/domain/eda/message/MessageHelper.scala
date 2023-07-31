package at.energydash
package domain.eda.message

import model.EbMsMessage
import domain.util.zip.CRC8
import model.enums.EbMsMessageType.{EDA_MSG_AUFHEBUNG_CCMC, EDA_MSG_AUFHEBUNG_CCMI, EDA_MSG_AUFHEBUNG_CCMS, EEG_BASE_DATA, ENERGY_FILE_RESPONSE, ENERGY_SYNC_REQ, EbMsMessageType, ONLINE_REG_ANSWER, ONLINE_REG_INIT, ZP_LIST}
import model.enums.EbMsProcessType.{EbMsProcessType, PROCESS_ENERGY_RESPONSE, PROCESS_LIST_METERINGPOINTS, PROCESS_METERINGPOINTS_VALUE, PROCESS_REGISTER_ONLINE, PROCESS_REVOKE_VALUE}

import com.google.common.io.BaseEncoding
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.zip.CRC32

object MessageHelper {

  var logger = LoggerFactory.getLogger("MessageHelper")
  def getEdaMessageByType(message: EbMsMessage): EdaMessage[_] = {
    message.messageCode match {
      case ONLINE_REG_INIT => CMRequestRegistrationOnlineMessage(message)
      case ZP_LIST => CPRequestZPListMessage(message)
      case EEG_BASE_DATA => CPRequestBaseDataMessage(message)
      case ENERGY_SYNC_REQ => CPRequestMeteringValueMessage(message)
      case EDA_MSG_AUFHEBUNG_CCMS => CMRevokeMessage(message)
    }
  }

  def getEdaMessageFromXml(messageCode: EbMsMessageType): EdaResponseType = {
    messageCode match {
      case ENERGY_FILE_RESPONSE => ConsumptionRecordMessage
      case ONLINE_REG_ANSWER | ONLINE_REG_INIT => CMRequestRegistrationOnlineMessage
    }
  }

  def getEdaMessageFromHeader(processCode: EbMsProcessType): Option[EdaResponseType] = {
    processCode match {
      case PROCESS_ENERGY_RESPONSE => Some(ConsumptionRecordMessage)
      case PROCESS_REGISTER_ONLINE => Some(CMRequestRegistrationOnlineMessage)
      case PROCESS_LIST_METERINGPOINTS => Some(CPRequestZPListMessage)
      case PROCESS_METERINGPOINTS_VALUE => Some(CPRequestMeteringValueMessage)
      case PROCESS_REVOKE_VALUE => Some(CMRevokeMessage)
      case _ =>
        logger.warn(s"Wrong ProcessCode: ${processCode}")
        None
    }
  }

  def EDAMessageCodeToProcessCode(msCode: EbMsMessageType): EbMsProcessType = {
    msCode match {
      case ZP_LIST => PROCESS_LIST_METERINGPOINTS
      case EEG_BASE_DATA => PROCESS_LIST_METERINGPOINTS
      case ONLINE_REG_INIT => PROCESS_REGISTER_ONLINE
      case ENERGY_SYNC_REQ => PROCESS_METERINGPOINTS_VALUE
      case EDA_MSG_AUFHEBUNG_CCMS | EDA_MSG_AUFHEBUNG_CCMC | EDA_MSG_AUFHEBUNG_CCMI => PROCESS_REVOKE_VALUE
    }
  }

  def buildRequestId(messageId: String): String = {
    val crc32 = new CRC32()
    crc32.update(messageId.getBytes)
    val crc32Val = crc32.getValue

    val crc8 = new CRC8
    crc8.reset()
    crc8.update(BigInt(crc32Val).toByteArray)

    val compose = BigInt((crc32Val << 8) + crc8.getValue).toByteArray
    BaseEncoding.base32().encode(compose.takeRight(5))
  }

  def buildMessageId(participant: String, seqNumber: Long): String = {
    val cal = Calendar.getInstance
    val dateTime = cal.getTime

    val dateFormat = new SimpleDateFormat("dd")
    val date = dateFormat.format(dateTime)

    val monthFormat = new SimpleDateFormat("MM")
    val month = monthFormat.format(dateTime)

    val yearFormat = new SimpleDateFormat("YYYY")
    val year = yearFormat.format(dateTime)

    s"${participant}${year}${month}${date}${dateTime.getTime / 10000}${formatSeqNumber(seqNumber)}"
  }

  def formatSeqNumber(seqNumber: Long) = f"${seqNumber}%010d"
}
