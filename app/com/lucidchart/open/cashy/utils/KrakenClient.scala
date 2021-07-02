package com.lucidchart.open.cashy.utils

import javax.inject.Inject
import org.apache.http.client.config.RequestConfig
import play.api.Logger
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.json.{JsObject, JsValue, JsBoolean, JsNumber}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import scala.io.Source

case class KrakenDisabledException() extends Exception("Kraken has been disabled")
case class KrakenFailedException(message: String) extends Exception(message)

class KrakenClient @Inject() (configuration: Configuration) {
  val logger = Logger(this.getClass)

  protected val enabled = configuration.getOptional[Boolean]("kraken.enabled").getOrElse(false)
  protected val apiKey = configuration.get[String]("kraken.apiKey")
  protected val apiSecret = configuration.get[String]("kraken.apiSecret")
  protected val uploadUrl = configuration.get[String]("kraken.imageUploadUrl")
  protected val usageUrl = configuration.get[String]("kraken.usageUrl")
  protected val connectionRequestTimeout = configuration.get[Int]("kraken.connectionRequestTimeoutMs")
  protected val connectTimeout = configuration.get[Int]("kraken.connectTimeoutMs")
  protected val socketTimeout = configuration.get[Int]("kraken.socketTimeoutMs")

  /**
    * @param sourceUrl the string url of the image that will be resized
    * @param width the width in pixels for the resized image
    * @param height the height in pixels for the resized image
    * @return the bytes of the resized image
    */
  def resizeImage(sourceUrl: String, width: Int, height: Int): Array[Byte] = {
    if (enabled) {

      val resizeParams = Json.obj(
        "resize" ->
          Json.obj(
            "width" -> JsNumber(width),
            "height" -> JsNumber(height),
            "strategy" -> "exact"
          ),
        "wait" -> JsBoolean(true),
        "lossy" -> JsBoolean(false),
        "url" -> sourceUrl
      )

      val krakenUrl = uploadToKraken(Json.stringify(authenticatedJson(Some(resizeParams))))
      DownloadHelper.download(krakenUrl).bytes
    } else {
      throw KrakenDisabledException()
    }
  }

  /**
    * @param sourceUrl the string url of the image that will be compressed
    * @return the bytes of the compressed image
    */
  def compressImage(sourceUrl: String): Array[Byte] = {
    if (enabled) {

      val resizeParams = Json.obj(
        "wait" -> JsBoolean(true),
        "lossy" -> JsBoolean(false),
        "url" -> sourceUrl
      )

      val krakenUrl = uploadToKraken(Json.stringify(authenticatedJson(Some(resizeParams))))
      DownloadHelper.download(krakenUrl).bytes
    } else {
      throw KrakenDisabledException()
    }
  }

  /**
    * @return the ratio of quota used if it was available, or None
    */
  def checkQuota(): Option[Double] = {
    val httpClient = HttpClientBuilder.create().build()

    try {
      val httpPost = new HttpPost(usageUrl)
      val body = new StringEntity(Json.stringify(authenticatedJson()))
      body.setContentType("application/json")
      httpPost.setEntity(body)
      httpPost.setConfig(getRequestConfig)
      val response = httpClient.execute(httpPost)

      try {
        // Get the kraken response
        if (response.getStatusLine().getStatusCode() != 200) {
          None
        } else {
          val responseBody = Source.fromInputStream(response.getEntity().getContent()).mkString
          val responseJson = Json.parse(responseBody)
          val success = (responseJson \ "success").asOpt[Boolean].getOrElse(false)
          if (success) {
            val used = (responseJson \ "quota_used").asOpt[Double]
            val total = (responseJson \ "quota_total").asOpt[Double]
            for (usedVal <- used; totalVal <- total) yield {
              usedVal / totalVal
            }
          } else {
            None
          }
        }
      } finally {
        response.close()
      }
    } finally {
      httpClient.close()
    }
  }

  /**
    * @param data optional JsObject to include with the auth data
    * @return the JsValue including auth data
    */
  private def authenticatedJson(data: Option[JsObject] = None): JsValue = {
    val authData = Json.obj(
      "auth" -> Json.obj(
        "api_key" -> apiKey,
        "api_secret" -> apiSecret
      )
    )
    val fullData = data match {
      case None => authData
      case Some(data) =>
        authData ++ data
    }

    Json.toJson(fullData)
  }

  /**
    * @param json the stringified json for the kraken upload request
    * @return the string URL of the krakened image
    */
  private def uploadToKraken(json: String): String = {
    val httpClient = HttpClientBuilder.create().build()

    try {
      val httpPost = new HttpPost(uploadUrl)
      val body = new StringEntity(json)
      body.setContentType("application/json")
      httpPost.setEntity(body)
      httpPost.setConfig(getRequestConfig)
      val response = httpClient.execute(httpPost)

      try {
        // Get the kraken response
        if (response.getStatusLine().getStatusCode() != 200) {
          val responseBody = Source.fromInputStream(response.getEntity().getContent()).mkString
          val responseJson = Json.parse(responseBody)
          val message = (responseJson \ "message").asOpt[String].getOrElse("Unknown error")
          throw new KrakenFailedException(s"${response.getStatusLine().getStatusCode()} [Kraken]: ${message}")
        } else {
          val responseBody = Source.fromInputStream(response.getEntity().getContent()).mkString
          val responseJson = Json.parse(responseBody)
          val success = (responseJson \ "success").asOpt[Boolean].getOrElse(false)
          if (success) {
            (responseJson \ "kraked_url").asOpt[String].get
          } else {
            val error = (responseJson \ "message").asOpt[String].getOrElse("Unknown error")
            throw new KrakenFailedException(error)
          }
        }
      } finally {
        response.close()
      }
    } finally {
      httpClient.close()
    }
  }

  private def getRequestConfig = {
    RequestConfig
      .custom()
      .setConnectionRequestTimeout(connectionRequestTimeout)
      .setConnectTimeout(connectTimeout)
      .setSocketTimeout(socketTimeout)
      .build()
  }

}
