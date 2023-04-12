package at.energydash
package services

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import actor.MqttPublisher.{MqttCommand, MqttPublish}
import domain.eda.message.MessageHelper
import model.enums.EbMsProcessType
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import scala.xml.InputSource


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
    }
//    .map(info => MessageHelper
//      .getEdaMessageFromHeader(EbMsProcessType.withName(info.processName))
//      .fromXML(scala.xml.XML.load(info.bodyPart.entity.dataBytes.runWith(StreamConverters.asInputStream(15.seconds))))).collect {
//        case Success(p) => p
////        case Failure(exception) => logger.error(exception.getMessage)
//      }
    .map(p => {
      mqttPublisher ! MqttPublish("", List(p))
    })
//    .log("filecontent", p => logger.info(s"$p"))
    .runWith(Sink.ignore)
  }
}

