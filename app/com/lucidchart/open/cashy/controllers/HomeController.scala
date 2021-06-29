package com.lucidchart.open.cashy.controllers

import javax.inject.Inject

import play.api.mvc.Action
import play.api.i18n.MessagesApi

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.oauth2.GoogleClient
import com.lucidchart.open.cashy.request.AuthAction

class HomeController @Inject() (val messagesApi: MessagesApi) extends AppController with play.api.i18n.I18nSupport {
  /**
   * Home / Intro / Welcome page.
   * Authentication not required
   */
  def index = AuthAction.maybeAuthenticatedUser { implicit userOption =>
    Action {  implicit request =>
      Ok(views.html.application.index())
    }
  }

}
