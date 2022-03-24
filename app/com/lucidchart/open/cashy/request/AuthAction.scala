package com.lucidchart.open.cashy.request

import javax.inject.Inject
import com.lucidchart.open.cashy.controllers.routes
import com.lucidchart.open.cashy.models.{User, UserModel}
import com.lucidchart.open.cashy.utils.Auth

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.streams.Accumulator

/**
  * Provides helpers for creating `AuthAction` values.
  */
trait AuthActionBuilder {

  protected def userModel: UserModel
  def auth: Auth

  /**
    * Action that checks for an active session and performs one of
    * two subactions (depending on existence of valid session).
    *
   * @param out action to call if user is logged out
    * @param in action to call if user is logged in
    */
  def logged(out: => EssentialAction)(in: (Auth.Session) => EssentialAction): EssentialAction =
    new EssentialAction {
      def apply(requestHeader: RequestHeader) = {
        auth.parseAuthCookie(requestHeader) match {
          case None =>
            out(requestHeader)
          case Some(session) =>
            in(session)(requestHeader)
        }
      }
    }

  /**
    * The action used for the loggedOut portion of the loggedIn function
    */
  def defaultOutAction =
    EssentialAction { requestHeader =>
      val isAjax = requestHeader.headers.get("X-Requested-With").map(_ == "XMLHttpRequest").getOrElse(false)

      if (isAjax) {
        Accumulator.done(Unauthorized)
      } else {
        val origDestCookie = requestHeader.method match {
          case "GET" => Cookie("origdest", requestHeader.path + "?" + requestHeader.rawQueryString)
          case _     => DiscardingCookie("origdest").toCookie
        }

        // we discard the auth cookie here mostly for dev purposes
        Accumulator.done(
          Redirect(routes.HomeController.index)
            .discardingCookies(auth.discardingCookie)
            .withCookies(origDestCookie)
            .flashing(AppFlash.error("Must be logged in to view that page"))
        )
      }
    }

  /**
    * Action to ensure the requester is logged in.
    */
  def loggedIn(block: => EssentialAction): EssentialAction = loggedIn { session: Auth.Session => block }

  /**
    * Action to ensure the requester is logged in.
    */
  def loggedIn(block: (Auth.Session) => EssentialAction): EssentialAction = logged(defaultOutAction)(block)

  /**
    * The action used for the loggedIn portion of the loggedOut function
    */
  def defaultInAction(session: Auth.Session) =
    EssentialAction { requestHeader =>
      Accumulator.done(Redirect(routes.HomeController.index))
    }

  /**
    * Action to ensure the requester is logged out.
    */
  def loggedOut(block: => EssentialAction): EssentialAction = logged(block)(defaultInAction)

  /**
    * Action to check whether or not a user is logged in
    */
  def maybeAuthenticatedUser(block: (Option[User]) => EssentialAction): EssentialAction =
    logged {
      block(None)
    } { session =>
      block(userModel.findById(session.userId))
    }

  /**
    * Action to ensure an authenticated user is logged in
    */
  def authenticatedUser(block: () => EssentialAction): EssentialAction =
    authenticatedUser { (session, user) => block() }

  /**
    * Action to ensure an authenticated user is logged in
    */
  def authenticatedUser(block: (User) => EssentialAction): EssentialAction =
    authenticatedUser { (session, user) => block(user) }

  /**
    * Action to ensure an authenticated user is logged in
    */
  def authenticatedUser(block: (Auth.Session, User) => EssentialAction): EssentialAction =
    loggedIn { session: Auth.Session =>
      userModel.findById(session.userId) match {
        case Some(user) => block(session, user)
        case None       => defaultOutAction
      }
    }
}

/**
  * Helper object to create `AuthAction` values.
  */
class AuthAction @Inject() (val auth: Auth, protected val userModel: UserModel) extends AuthActionBuilder {}
