package com.lucidchart.open.cashy

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.concurrent.Akka
import akka.actor.Props
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import play.api.Play.current

object Global extends GlobalSettings {

  override def onStart(application: Application) {
    super.onStart(application)
  }

}
