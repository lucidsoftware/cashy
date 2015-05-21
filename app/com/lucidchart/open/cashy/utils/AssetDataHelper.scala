package com.lucidchart.open.cashy.utils

import java.io._
import play.api.mvc.MultipartFormData.FilePart

case class AssetNotFoundException(message: String) extends Exception(message)

case class AssetData(
  bytes: Array[Byte],
  contentType: Option[String],
  filename: String
)

object AssetDataHelper {
  def getData(file: Option[FilePart[Array[Byte]]], url: Option[String]): AssetData = {
    // Prefer file over URL
    parseFile(file) orElse download(url) getOrElse(throw AssetNotFoundException(s"Could not parse file and/or could not download from $url"))
  }

  private def download(urlOption: Option[String]): Option[AssetData] = {
    urlOption.map { url =>
      val downloadResult = DownloadHelper.download(url)
      val queryStringIndex = url.lastIndexOf("?")
      val separatorIndex = url.lastIndexOf("/")
      val filename = if(queryStringIndex > separatorIndex) {
        url.substring(separatorIndex, queryStringIndex)
      } else {
        url.substring(separatorIndex)
      }
      AssetData(downloadResult.bytes, downloadResult.contentType, filename)
    }
  }

  private def parseFile(fileOption: Option[FilePart[Array[Byte]]]): Option[AssetData] = {
    fileOption.map { file =>
      AssetData(file.ref, file.contentType, file.filename)
    }
  }
}