package at.energydash
package domain.eda.messages

import domain.eda.message.CMRequestRegistrationOnlineMessageV0100
import model.JsonImplicit._

import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source
import scala.util.{Failure, Success}

class CMRequestRegistrationOnlineMessageV0100Spec extends AnyWordSpec {
  "Energy XML File" should {
    "Parse from XML" in {
      //      val xmlFile = scala.xml.XML.load(new FileInputStream("/home/petero/projects/energycash/xml/DATEN_CRMSG/message-daten_crmsg.xml"))
      val xmlFile = scala.xml.XML.load(Source.fromResource("message-abschluss-econ.xml").reader())
      CMRequestRegistrationOnlineMessageV0100.fromXML(xmlFile) match {
        case Success(m) =>
          m.asJson.deepDropNullValues.noSpaces.toString()
          assert(true)
        case Failure(exception) =>
          exception.toString
          fail(exception)
      }
    }
  }

}
