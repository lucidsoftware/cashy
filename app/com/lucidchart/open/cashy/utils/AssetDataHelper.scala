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
    parseFile(file).getOrElse {
      download(url).getOrElse {
        throw AssetNotFoundException(s"Could not parse file and/or could not download from $url")
      }
    }
  }

  private def download(urlOption: Option[String]): Option[AssetData] = {
    urlOption.map { url =>
      val downloadResult = DownloadHelper.download(url)
      val filename = url.substring(url.lastIndexOf("/"))
      AssetData(downloadResult.bytes, downloadResult.contentType, filename)
    }
  }

  private def parseFile(fileOption: Option[FilePart[Array[Byte]]]): Option[AssetData] = {
    fileOption.map { file =>
      file match {
        case FilePart(key, filename, contentType, bytes) => {
          AssetData(bytes, contentType, filename)
        }
      }
    }
  }
}