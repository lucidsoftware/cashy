package com.lucidchart.open.cashy.controllers

import javax.inject.Inject

import play.api.mvc.{Action, ControllerComponents, AbstractController}

import com.lucidchart.open.cashy.config.Buckets
import com.lucidchart.open.cashy.oauth2.GoogleClient
import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.views

class HomeController @Inject() (authAction: AuthAction, components: ControllerComponents)(implicit
    buckets: Buckets
) extends AbstractController(components)
    with play.api.i18n.I18nSupport {

  /**
    * Home / Intro / Welcome page.
    * Authentication not required
    */
  def index =
    authAction.maybeAuthenticatedUser { implicit userOption =>
      Action { implicit request =>
        Ok(views.html.application.index())
      }
    }

}
