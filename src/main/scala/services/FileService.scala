package at.energydash
package services

import actor.MqttPublisher.{MqttCommand, MqttPublish}
import domain.eda.message.{EdaErrorMessage, MessageHelper}
import model.enums.EbMsProcessType
import model.EbMsMessage
import model.enums.EbMsMessageType

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, StreamConverters}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

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
  def handleUpload(formData: Multipart.FormData): Future[Done] = {
  formData.parts
    .map(part => FileInfo(part))
    .log("fileInfo", info => logger.info(s"$info Size: ${info.bodyPart.entity.withoutSizeLimit()}"))
    .mapAsync(1)(info => info.bodyPart.toStrict(5.seconds).map { body =>  MessageHelper
      .getEdaMessageFromHeader(EbMsProcessType.withName(info.processName))
      .fromXML(scala.xml.XML.load(body.entity.dataBytes.runWith(StreamConverters.asInputStream(15.seconds))))
    })
    .collect {
      case Success(p) => p
      case Failure(exception) => {
        logger.error(exception.toString)
        EdaErrorMessage(EbMsMessage(
          messageCode = EbMsMessageType.ERROR_MESSAGE,
          conversationId = "1",
          messageId = None,
          sender = "",
          receiver = "",
          errorMessage = Some(exception.toString)))
      }
    }
//    .log("mqtt", info => logger.info(s"$info Size: ${info}"))
    .map(p => {
      mqttPublisher ! MqttPublish("", List(p))
    })
    .runWith(Sink.ignore)
  }
}

