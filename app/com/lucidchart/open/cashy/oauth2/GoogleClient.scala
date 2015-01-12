package com.lucidchart.open.cashy.oauth2

import com.google.api.client.auth.oauth2._
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.googleapis.auth.oauth2._
import play.api.Play.current
import play.api.Play.configuration
import play.api.libs.json._
import scala.collection.JavaConverters._
import scala.io.Source
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

case class GoogleUser(id: String, email: String)

object GoogleClient extends GoogleClient
class GoogleClient {

  val googleClientId = configuration.getString("auth.google.clientId").get
  val googleSecret = configuration.getString("auth.google.secret").get
  val googleOauthEndpoint = configuration.getString("auth.google.oauthEndpoint").get
  val googleTokenEndpoint = configuration.getString("auth.google.tokenEndpoint").get
  val googleTokenInfoEndpoint = configuration.getString("auth.google.tokenInfoEndpoint").get

  private val authFlow = new AuthorizationCodeFlow.Builder(
    BearerToken.authorizationHeaderAccessMethod,
    new NetHttpTransport(),
    new JacksonFactory(),
    new GenericUrl(googleTokenEndpoint),
    new ClientParametersAuthentication(googleClientId, googleSecret),
    googleClientId,
    googleOauthEndpoint
    )
    .setCredentialDataStore(CustomDataStore)
    .setScopes(List("email","profile").asJava)
    .build()

  def isAuthorized(userId: Long): Boolean = {
    authFlow.loadCredential(userId.toString) != null
  }

  def requestAuthorization(state: String, redirectUri: String): String = {
    authFlow.newAuthorizationUrl().setRedirectUri(redirectUri).setState(state).build()
  }

  def requestToken(authorizationCode: String, redirectUri: String) = {
    authFlow.newTokenRequest(authorizationCode)
      .setRedirectUri(redirectUri)
      .execute()
  }

  def setCredential(tokenResponse: TokenResponse, userId: Long) {
    authFlow.createAndStoreCredential(tokenResponse, userId.toString)
  }

  //https://www.googleapis.com/oauth2/v1/tokeninfo?id_token=
  // Decodes an id_token and extracts the email
  def getGoogleUser(tokenResponse: TokenResponse): GoogleUser = {
    // Parse the json of the token response
    val tokenJson = Json.parse(tokenResponse.toString)
    val idToken = (tokenJson \ "id_token").asOpt[String]

    idToken match {
      case None => throw new Exception("No id token in token response")
      case Some(idToken) => {
        // Call the tokeninfo api
        val tokenUrl = googleTokenInfoEndpoint + "?id_token=" + idToken

        val httpClient = HttpClientBuilder.create().build()
        val response = httpClient.execute(new HttpGet(tokenUrl))

        // Parse the response for email
        val body = if (response.getStatusLine().getStatusCode() != 200) {
          throw new Exception("Could not retrieve decoded id token")
        } else {
          val rawBody = Source.fromInputStream(response.getEntity().getContent()).getLines().mkString("\n")
          Json.parse(rawBody)
        }
        val email = (body \ "email").asOpt[String].getOrElse("")
        val id = (body \ "user_id").asOpt[String].getOrElse("")
        GoogleUser(id, email)
      }
    }
  }

  def deleteCredential(key: String) {
    CustomDataStore.delete(key)
  }

}