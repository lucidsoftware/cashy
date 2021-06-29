package com.lucidchart.open.cashy.controllers

import javax.inject.Inject
import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.models.{AssetModel, Asset, FolderModel, Folder}
import com.lucidchart.open.cashy.views
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Action
import play.api.i18n.MessagesApi

case class SearchParams(
  q: String
)

class SearchController @Inject() (val messagesApi: MessagesApi) extends AppController with play.api.i18n.I18nSupport {
  import SearchController._

  def search = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      searchForm.bindFromRequest.fold(
        formWithErrors => Ok(views.html.search.index(formWithErrors, Nil, None)),
        data => {

          val assets = AssetModel.search(data.q)

          // Get the possible folder keys for the bucket
          val bucketFolders: Map[String,List[String]] = assets.groupBy(_.bucket).map { case (bucket, assets) =>
            (bucket, assets.map { asset =>
              // Get all of the possible parent folders for an asset
              parentPaths(asset.key)
            }.flatten)
          }.toMap

          // Get the hidden folders for each bucket that start with a key
          val hiddenFolders: List[Folder] = bucketFolders.map { case (bucket, folders) =>
            FolderModel.findByKeys(bucket, folders).filter(_.hidden)
          }.toList.flatten

          // If an asset is inside of a hidden folder, mark it as hidden for search result purposes
          val viewAssets = assets.map { asset =>
            val hidden = hiddenFolders.exists(folder => folder.bucket == asset.bucket && asset.key.startsWith(folder.key))
            asset.copy(hidden=hidden)
          }

          Ok(views.html.search.index(searchForm.bindFromRequest, viewAssets, Some(data.q)))
        }
      )
    }
  }

  private def parentPaths(assetPath: String): List[String] = {
    val folders = assetPath.split("/").dropRight(1)
    folders.zipWithIndex.map { case (crumb,idx) =>
      folders.take(idx+1).mkString("/")+"/"
    }.toList
  }
}

object SearchController {
  val searchForm = Form(
    mapping(
      "q" -> text.verifying("Enter a search term", x => x != "")
    )(SearchParams.apply)(SearchParams.unapply)
  )

}
