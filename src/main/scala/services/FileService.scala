package at.energydash
package services

import actor.MqttPublisher.{EdaNotification, MqttCommand, MqttPublish}
import domain.eda.message.{EdaErrorMessage, MessageHelper}
import model.EbMsMessage
import model.enums.{EbMsMessageType, EbMsProcessType}

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.{BodyPartEntity, Multipart}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.http.scaladsl.unmarshalling.Unmarshaller


trait FileService {
  implicit val system: ActorSystem[_]
  implicit val mat: Materializer

  def handleUpload(formData: Multipart.FormData): Future[Done]
}

object FileService {
  def apply(system: ActorSystem[_], mqttPublisher: ActorRef[MqttCommand])(implicit mat: Materializer) =
    new FileServiceImpl(system, mqttPublisher)
}
class FileServiceImpl(val system: ActorSystem[_], mqttPublisher: ActorRef[MqttCommand])(implicit val mat: Materializer) extends FileService with StrictLogging {
  import system._

  implicit def bp2sting(implicit ev: Unmarshaller[String, String]): Unmarshaller[BodyPartEntity, String] = Unmarshaller.withMaterializer { implicit executionContext =>
    implicit mat =>
      entity =>
        entity.dataBytes
          .runWith(Sink.fold(ByteString.empty)((accum, bs) => accum.concat(bs)))
          .map(_.decodeString(java.nio.charset.StandardCharsets.UTF_8))
          .flatMap(ev.apply(_))
  }
  private def bodyPart2String(body: BodyPart): Future[String] = Unmarshal(body.entity).to[String]

  private def bodyPart2Xml(body: BodyPart) = bodyPart2String(body).map(scala.xml.XML.loadString)

  private def edaErrorMessage(error: String) = {
    EdaErrorMessage(EbMsMessage(
                  messageCode = EbMsMessageType.ERROR_MESSAGE,
                  conversationId = "1",
                  messageId = None,
                  sender = "",
                  receiver = "",
                  errorMessage = Some(error)
                ))
  }

  def handleUpload(formData: Multipart.FormData): Future[Done] = {
  formData.parts
    .map(part => FileInfo(part))
    .mapAsync(1)(info => bodyPart2Xml(info.bodyPart).map(xml =>
      MessageHelper.getEdaMessageFromHeader(EbMsProcessType.withName(info.processName)) match {
        case Some(t) => t.fromXML(xml) match {
          case Success(p) => EdaNotification(info.processName, p)
          case Failure(exception) => EdaNotification("error", edaErrorMessage(exception.toString))
        }
        case None => EdaNotification("error", edaErrorMessage("Unknown process type"))
      }
    ))
    .map(p => mqttPublisher ! MqttPublish(List(p)))
    .runWith(Sink.ignore)
  }
}

