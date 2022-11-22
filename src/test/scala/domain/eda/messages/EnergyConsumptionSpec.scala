package at.energydash
package domain.eda.messages

import at.energydash.domain.eda.message.ConsumptionRecordMessage
import org.scalatest.wordspec.AnyWordSpec

import java.io.{FileInputStream, InputStream}
import scala.util.{Failure, Success}

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._

import at.energydash.model.JsonImplicit._

class EnergyConsumptionSpec extends AnyWordSpec {
  "Energy XML File" should {
    "Parse from XML" in {
      val xmlFile = scala.xml.XML.load(new FileInputStream("/home/petero/projects/energycash/xml/DATEN_CRMSG/message-220820_143600.xml"))
      println(ConsumptionRecordMessage.fromXML(xmlFile) match {
        case Success(m) => m.asJson.deepDropNullValues.noSpaces.toString()
        case Failure(exception) => exception.toString
      })
    }
  }
}
