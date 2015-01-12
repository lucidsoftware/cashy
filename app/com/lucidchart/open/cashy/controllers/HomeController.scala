package com.lucidchart.open.cashy.controllers

import play.api.mvc.Action

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.oauth2.GoogleClient
import com.lucidchart.open.cashy.request.AuthAction

class HomeController extends AppController {
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

object HomeController extends HomeController
