package com.lucidchart.open.cashy.utils

import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import play.api.libs.json.Json
import play.api.libs.json.{JsObject, JsValue, JsBoolean, JsNumber}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import scala.io.Source

case class KrakenDisabledException() extends Exception("Kraken has been disabled")
case class KrakenFailedException(message: String) extends Exception(message)

object KrakenClient extends KrakenClient
class KrakenClient {
  val logger = Logger(this.getClass)

  protected val enabled = configuration.getBoolean("kraken.enabled").getOrElse(false)
  protected val apiKey = configuration.getString("kraken.apiKey").get
  protected val apiSecret = configuration.getString("kraken.apiSecret").get
  protected val uploadUrl = configuration.getString("kraken.imageUploadUrl").get

  def resizeImage(sourceUrl: String, width: Int, height: Int): Array[Byte] = {
    if(enabled) {

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

      val krakenUrl = makeRequest(Json.stringify(authenticatedJson(resizeParams))).get
      DownloadHelper.downloadBytes(krakenUrl)
    } else {
      throw KrakenDisabledException()
    }
  }

  def compressImage(sourceUrl: String): Array[Byte] = {
    if(enabled) {

      val resizeParams = Json.obj(
        "wait" -> JsBoolean(true),
        "lossy" -> JsBoolean(false),
        "url" -> sourceUrl
      )

      val krakenUrl = makeRequest(Json.stringify(authenticatedJson(resizeParams))).get
      DownloadHelper.downloadBytes(krakenUrl)
    } else {
      throw KrakenDisabledException()
    }
  }

  private def authenticatedJson(data: JsObject): JsValue = {
    val fullData = Json.obj(
      "auth" -> Json.obj(
        "api_key" -> apiKey,
        "api_secret" -> apiSecret
      )
    ) ++ data
    Json.toJson(fullData)
  }

  private def makeRequest(json: String) = {
    val httpClient = HttpClientBuilder.create().build()

    val httpPost = new HttpPost(uploadUrl)
    val body = new StringEntity(json)
    body.setContentType("application/json")
    httpPost.setEntity(body)
    val response = httpClient.execute(httpPost)

    // Get the kraken response
    val krakenUrlOption = if (response.getStatusLine().getStatusCode() != 200) {
      val responseBody = Source.fromInputStream(response.getEntity().getContent()).mkString
      val responseJson = Json.parse(responseBody)
      val message = (responseJson \ "message").asOpt[String].getOrElse("Unknown error")
      throw new KrakenFailedException(response.getStatusLine().getStatusCode() + " [Kraken]: " + message)
    } else {
      val responseBody = Source.fromInputStream(response.getEntity().getContent()).mkString
      val responseJson = Json.parse(responseBody)
      val success = (responseJson \ "success").asOpt[Boolean].getOrElse(false)
      if (success) {
        (responseJson \ "kraked_url").asOpt[String]
      } else {
        None
      }
    }
    krakenUrlOption
  }

}