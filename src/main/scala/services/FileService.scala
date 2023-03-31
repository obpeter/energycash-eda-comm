package at.energydash
package services

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, StreamConverters}
import at.energydash.actor.MqttPublisher.{MqttCommand, MqttPublish}
import at.energydash.domain.eda.message.MessageHelper
import at.energydash.model.enums.EbMsProcessType
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.Success


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
  def handleUpload(formData: Multipart.FormData): Future[Done] = {
  formData.parts
    .map(part => FileInfo(part))
    .log("fileInfo", info => logger.info(s"$info"))
    .map(info => MessageHelper
      .getEdaMessageFromHeader(EbMsProcessType.withName(info.processName))
      .fromXML(scala.xml.XML.load(info.bodyPart.entity.dataBytes.runWith(StreamConverters.asInputStream())))).collect {
        case Success(p) => p
      }
    .map(p => {
      mqttPublisher ! MqttPublish("", List(p))
    })
//    .log("filecontent", p => logger.info(s"$p"))
    .runWith(Sink.ignore)
  }
}

