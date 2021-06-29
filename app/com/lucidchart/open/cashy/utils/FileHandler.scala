package com.lucidchart.open.cashy.utils

// COde modified from http://stackoverflow.com/questions/15036121/pulling-files-from-multipartformdata-in-memory-in-play2-scala

import play.core.parsers.Multipart.{handleFilePart, PartHandler, FileInfo}
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.iteratee.Iteratee
import java.io.ByteArrayOutputStream
import play.api.mvc.BodyParser
import scala.concurrent.ExecutionContext.Implicits.global

object FileHandler extends FileHandler
class FileHandler {
	def handleFilePartAsByteArray: PartHandler[FilePart[Array[Byte]]] = handleFilePart {
		case FileInfo(partName, filename, contentType) =>
			Iteratee.fold[Array[Byte], ByteArrayOutputStream](
				new ByteArrayOutputStream()) { (os, data) =>
				  os.write(data)
				  os
				}.map { os =>
				  os.close()
				  os.toByteArray
				}
	}
}
