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

    try {
      val httpGet = new HttpGet(url)
      val response = httpClient.execute(httpGet)

      val statusCode = response.getStatusLine().getStatusCode()

      if (statusCode != 200) {
        throw new DownloadFailedException("Did not get a status of 200 from " + url)
      } else {
        val baos = new java.io.ByteArrayOutputStream
        response.getEntity().writeTo(baos)
        baos.close
        val bytes = baos.toByteArray
        val contentType = Option(response.getEntity().getContentType().getValue())
        DownloadResult(bytes, contentType)
      }
    } catch {
      case e: Exception => {
        throw new DownloadFailedException("Unable to download from " + url + ": " + e.getMessage)
      }
    } finally {
      httpClient.close()
    }
  }
}
