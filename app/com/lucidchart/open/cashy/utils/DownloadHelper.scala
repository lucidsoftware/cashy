package com.lucidchart.open.cashy.utils

import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder

case class DownloadFailedException(message: String) extends Exception(message)

case class DownloadResult(
  bytes: Array[Byte],
  contentType: Option[String]
)

object DownloadHelper extends DownloadHelper
class DownloadHelper {

  def download(url: String): DownloadResult = {
    val httpClient = HttpClientBuilder.create().build()

    val httpGet = new HttpGet(url)
    val response = httpClient.execute(httpGet)

    val statusCode = response.getStatusLine().getStatusCode()
    val bytes = if (statusCode != 200) {
      throw new DownloadFailedException("Could not retrieve bytes")
    } else {
      val baos = new java.io.ByteArrayOutputStream
      response.getEntity().writeTo(baos)
      baos.close
      baos.toByteArray
    }
    val contentType = Option(response.getEntity().getContentType().getValue())

    DownloadResult(bytes, contentType)
  }
}