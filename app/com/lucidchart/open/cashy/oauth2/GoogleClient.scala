package com.lucidchart.open.cashy.oauth2

import javax.inject.Inject
import com.google.api.client.auth.oauth2._
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import play.api.Configuration
import play.api.libs.json._
import scala.jdk.CollectionConverters._
import scala.io.Source
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

case class GoogleUser(id: String, email: String)

class GoogleClient @Inject() (customDataStore: CustomDataStore, configuration: Configuration) {

  val googleClientId = configuration.get[String]("auth.google.clientId")
  val googleSecret = configuration.get[String]("auth.google.secret")
  val googleOauthEndpoint = configuration.get[String]("auth.google.oauthEndpoint")
  val googleTokenEndpoint = configuration.get[String]("auth.google.tokenEndpoint")
  val googleTokenInfoEndpoint = configuration.get[String]("auth.google.tokenInfoEndpoint")

  private val authFlow = new AuthorizationCodeFlow.Builder(
    BearerToken.authorizationHeaderAccessMethod,
    new NetHttpTransport(),
    new JacksonFactory(),
    new GenericUrl(googleTokenEndpoint),
    new ClientParametersAuthentication(googleClientId, googleSecret),
    googleClientId,
    googleOauthEndpoint
  ).setCredentialDataStore(customDataStore)
    .setScopes(List("email", "profile").asJava)
    .build()

  def isAuthorized(userId: Long): Boolean = {
    authFlow.loadCredential(userId.toString) != null
  }

  def requestAuthorization(state: String, redirectUri: String): String = {
    authFlow.newAuthorizationUrl().setRedirectUri(redirectUri).setState(state).build()
  }

  def requestToken(authorizationCode: String, redirectUri: String) = {
    authFlow
      .newTokenRequest(authorizationCode)
      .setRedirectUri(redirectUri)
      .execute()
  }

  def setCredential(tokenResponse: TokenResponse, userId: Long): Unit = {
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

  def deleteCredential(key: String): Unit = {
    customDataStore.delete(key)
  }

}
