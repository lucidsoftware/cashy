package com.lucidchart.open.cashy.controllers

import play.api.mvc.Action

import com.lucidchart.open.cashy.views

class HomeController extends AppController {
	/**
	 * Home / Intro / Welcome page.
	 * Authentication not required
	 */
	def index = Action {  implicit request =>
		Ok(views.html.application.index())
	}
}

object HomeController extends HomeController
