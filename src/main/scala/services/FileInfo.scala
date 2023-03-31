package at.energydash
package services

import akka.http.scaladsl.model.{FormData, HttpHeader, Multipart}
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString

import java.io.InputStream

case class FileInfo(bodyPart: Multipart.BodyPart, processName: String) {
}

object FileInfo {
  def apply (part: Multipart.FormData.BodyPart): FileInfo = {
    new FileInfo(part, part.name)
  }
}
