package com.lucidchart.open.cashy.utils

import java.io._
import play.api.mvc.MultipartFormData.FilePart

case class AssetData(
    bytes: Array[Byte],
    contentType: Option[String],
    filename: String
)

object AssetDataHelper {
  def getData(source: Either[FilePart[Array[Byte]], String]): AssetData = {
    source match {
      case Left(file) => {
        parseFile(file)
      }
      case Right(url) => {
        download(url)
      }
    }
  }

  private def download(url: String): AssetData = {
    val downloadResult = DownloadHelper.download(url)
    val queryStringIndex = url.lastIndexOf("?")
    val separatorIndex = url.lastIndexOf("/")
    val filename = if (queryStringIndex > separatorIndex) {
      url.substring(separatorIndex, queryStringIndex)
    } else {
      url.substring(separatorIndex)
    }
    AssetData(downloadResult.bytes, downloadResult.contentType, filename)
  }

  private def parseFile(file: FilePart[Array[Byte]]): AssetData = {
    AssetData(file.ref, file.contentType, file.filename)
  }
}
