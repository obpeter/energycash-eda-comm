package at.energydash
package domain.eda.messages

import domain.eda.message.MessageHelper
import domain.util.zip.CRC8

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UtilSpec extends AnyWordSpec with Matchers {

  "Checksum" should {
    "CRC 8" in {
      val crc8 = new CRC8
      crc8.reset()
      crc8.update("test".getBytes)

      crc8.getValue shouldBe(181)
    }

//    "CRC 32" in {
//      val crc32 = new CRC32()
//      crc32.update("RC100130202210100813466960000000346".getBytes)
////      crc32.update("AT999999201812312359598880000000001".getBytes)
//      val crc32Val = crc32.getValue
//
//      println(crc32Val)
//      println((crc32Val << 8).toHexString)
//      println(crc32Val.toHexString)
//
//      val crc8 = new CRC8
//      crc8.reset()
//      crc8.update(BigInt(crc32Val).toByteArray)
//
//      println("----- len")
//      println(BigInt(crc32Val).toByteArray.length)
//      println("----- bin")
//      println(crc32Val.toBinaryString)
//      println("----- bin")
//
//      println(crc8.getValue.toHexString)
//      println(BigInt(((crc32Val.toInt << 8) + crc8.getValue.toInt)))
//
//      val compose = BigInt(((crc32Val << 8) + crc8.getValue)).toByteArray
//      compose.foreach(i => print(i.toHexString))
//      println()
//      println("---")
//      compose.foreach(i => print(s"${i.toChar.toHexString}#${i.toShort} | "))
//      println()
//      println("length: ", compose.take(4).length)
//      val myVal = (((crc32Val & 0xFFFFFFFF) << 8)+crc8.getValue)
//      println(s"crc32Val=${myVal.toHexString}")
//      println(s"Array: ${BigInt(myVal).toByteArray.mkString("Array(", ", ", ")")}")
//      println(s"Array: ${BigInt(myVal).toByteArray.takeRight(5).mkString("Array(", ", ", ")")}")
//      println(BaseEncoding.base32().encode(compose.takeRight(5)))
//      println("--- BASE32")
//
//      val byteArray = ByteBuffer.allocate(8).putLong(crc32Val).array()
//      byteArray.foreach(i => print(s"${i.toHexString}%${i} | "))
//      println()
//      println(byteArray.length)
//      println(BaseEncoding.base32().encode(byteArray))
//
//      println("-------------")
//      val t = Array[Byte](0x01, 0x02, 0x03, 0x04, 0x05)
//      println(BaseEncoding.base32().encode(t))
//    }

    "be calculated" in {

      val input = "AT999999201812312359598880000000001"
      MessageHelper.buildRequestId(input) shouldBe("IWRN74PW")
      MessageHelper.buildRequestId("RC100130202210100813466960000000346") shouldBe("XMNN57RC")
      MessageHelper.buildRequestId("RC100130202210051500513360000000490") shouldBe("67AVVWVN")
    }

    "build Message-ID" in {
      val participant = "AT999999"
//      val date = Calendar.getInstance
//
//      println(s"${participant}${date.get(Calendar.YEAR)}${date.get(Calendar.MONTH)}${date.get(Calendar.DAY_OF_MONTH)}")

      println(MessageHelper.buildMessageId(participant, 1))
    }
  }
}
