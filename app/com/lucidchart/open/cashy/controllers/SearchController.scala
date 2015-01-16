package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.models.AssetModel
import com.lucidchart.open.cashy.views
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Action

case class SearchParams(
  q: String
)

object SearchController extends SearchController
class SearchController extends AppController {
  val searchForm = Form(
    mapping(
      "q" -> text.verifying("Enter a search term", x => x != "")
    )(SearchParams.apply)(SearchParams.unapply)
  )

  def search = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      searchForm.bindFromRequest.fold(
        formWithErrors => Ok(views.html.search.index(formWithErrors, Nil)),
        data => Ok(views.html.search.index(searchForm.bindFromRequest, AssetModel.search(data.q)))
      )
    }
  }
}
