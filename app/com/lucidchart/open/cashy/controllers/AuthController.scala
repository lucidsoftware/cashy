package com.lucidchart.open.cashy.controllers

import play.api.mvc.Action

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.models.UserModel
import com.lucidchart.open.cashy.utils.{Auth, HashHelper}
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.oauth2.GoogleClient

import play.api.Play.current
import play.api.Play.configuration
import play.api.mvc.Cookie
import play.api.mvc.DiscardingCookie
import play.api.mvc.RequestHeader
import scala.collection.JavaConverters._

class AuthController extends AppController {

  val applicationSecret = configuration.getString("application.secret").get
  val domainWhitelist: List[String] = configuration.getStringList("auth.google.domainWhitelist").get.asScala.toList

  protected def loginRedirect(userId: Long, rememberme: Boolean)(implicit request: RequestHeader) = {
    val authCookie = Auth.generateAuthCookie(userId, rememberme, request.headers.get("User-Agent").getOrElse(""))
    val origDestCookie = DiscardingCookie("origdest")
    val destination = request.cookies.get("origdest").map(_.value).getOrElse(routes.HomeController.index().url)
    Redirect(destination).withCookies(authCookie).discardingCookies(origDestCookie)
  }

  def login = AuthAction.maybeAuthenticatedUser { user =>
    Action {  implicit request =>

      if (user.isDefined) {
        Redirect(routes.HomeController.index())
      }

      // Generate state string to check that the request is not altered later
      val state =  scala.util.Random.alphanumeric.take(30).mkString
      val oauthCheckCookie = Cookie(
        "oauth-check",
        HashHelper.sha1(applicationSecret + state),
        None
      )

      val googleUrl = GoogleClient.requestAuthorization(state, routes.AuthController.oauth2Callback(None, None).absoluteURL(true))

      Redirect(googleUrl).withCookies(oauthCheckCookie)
    }
  }

  def oauth2Callback(code: Option[String] = None, state: Option[String] = None) = Action { implicit request =>
    // Check the oauth-check cookie
    request.cookies.get("oauth-check") match {
      case None => Redirect(routes.HomeController.index()).flashing(AppFlash.error("The Oauth request failed. Are your cookies turned on?", "Oauth Failure"))
      case Some(oauthCheckCookie) => {
        // If there is no code or state they did not authorize the app
        if (!code.isDefined || !state.isDefined) {
          Redirect(routes.HomeController.index()).flashing(AppFlash.error("The Oauth request failed. Did you approve the app permissions?", "Oauth Failure"))
        } else {
          if (HashHelper.sha1(applicationSecret + state.get) != oauthCheckCookie.value) {
            Unauthorized
          } else {
            // Get the token and google user
            val token = GoogleClient.requestToken(code.get, routes.AuthController.oauth2Callback(None, None).absoluteURL(true))
            val googleUser = GoogleClient.getGoogleUser(token)

            // Make sure the user's email is in the white list of domains
            if (!verifyEmailDomain(googleUser.email)) {
              Redirect(routes.HomeController.index()).flashing(AppFlash.error("Your google account is not on an allowed domain", "Invalid domain"))
            } else {
              // Look for a cashy user by google id
              val user = UserModel.findByGoogleId(googleUser.id)

              val userId = user match {
                case None => {
                  val user = UserModel.createUser(googleUser.id, googleUser.email)
                  user.id
                }
                case Some(user) => user.id
              }

              // Store the credential with the user id
              GoogleClient.setCredential(token, userId)

              loginRedirect(userId, false).discardingCookies(DiscardingCookie("oauth-check"))
            }
          }
        }
      }
    }
  }

  def logout = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      Redirect(routes.HomeController.index()).discardingCookies(Auth.discardingCookie)
    }
  }

  private def verifyEmailDomain(email: String): Boolean = {
    try {
      // Split the email at @
      val domain = email.split("@")(1)
      domainWhitelist.contains(domain)
    }
    catch {
      case e: Exception => false
    }
  }
}

object AuthController extends AuthController
