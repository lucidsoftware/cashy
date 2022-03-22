package com.lucidchart.open.cashy.utils

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.core.parsers.Multipart.{FilePartHandler, FileInfo}
import play.api.mvc.MultipartFormData.FilePart
import java.io.ByteArrayOutputStream
import play.api.mvc.BodyParser
import play.api.libs.streams.Accumulator
import scala.concurrent.ExecutionContext

object FileHandler extends FileHandler
class FileHandler {
  def handleFilePartAsByteArray(implicit ec: ExecutionContext): FilePartHandler[Array[Byte]] =
    (info: FileInfo) =>
      Accumulator(Sink.reduce[ByteString](_ ++ _)).map { bytes =>
        val byteArray: Array[Byte] = bytes.toArray
        new FilePart(
          info.partName,
          info.fileName,
          info.contentType,
          byteArray,
          byteArray.length,
          info.dispositionType
        )
      }
}
