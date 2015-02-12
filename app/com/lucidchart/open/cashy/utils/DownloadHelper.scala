package com.lucidchart.open.cashy.utils

import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder

case class DownloadFailedException(message: String) extends Exception(message)

object DownloadHelper extends DownloadHelper
class DownloadHelper {

  def downloadBytes(url: String): Array[Byte] = {
    val httpClient = HttpClientBuilder.create().build()

    val httpGet = new HttpGet(url)
    val response = httpClient.execute(httpGet)

    val statusCode = response.getStatusLine().getStatusCode()
    if (statusCode < 200 || statusCode >= 300) {
      throw new DownloadFailedException("Could not retrieve bytes")
    } else {
      val baos = new java.io.ByteArrayOutputStream
      response.getEntity().writeTo(baos)
      baos.close
      baos.toByteArray
    }
  }
}