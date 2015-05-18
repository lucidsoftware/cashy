package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.models.{BrowseItem, BrowseItemType, BrowseItemDetail, AssetModel, UserModel, FolderModel}
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.amazons3.ListObjectsResponse

import play.api.Logger
import play.api.mvc.{Request, Action}
import play.api.libs.json.Json
import play.api.Play.current
import play.api.Play.configuration
import org.apache.http.client.methods.HttpHead
import org.apache.http.impl.client.HttpClientBuilder
import scala.io.Source

object BrowseController extends BrowseController
class BrowseController extends AppController {

  val logger = Logger(this.getClass)

  val fullS3AccessUrl = configuration.getString("amazon.s3.fullAccessUrl").get

  /**
   * Browse page, authentication required
   */
  def index(bucket: String, path: String, marker: Option[String]) = AuthAction.authenticatedUser { implicit user =>
    Action {  implicit request =>
      val crumbs = breadcrumbs(path)

      val browsePath = if (path.endsWith("/") || path.isEmpty) path else path + "/"

      val listResponse = S3Client.listObjects(bucket, browsePath, marker)

      val folders = FolderModel.findByKeys(bucket, listResponse.folders).map(folder => (folder.key, folder)).toMap

      val folderItems = listResponse.folders.map(f =>
        BrowseItem(BrowseItemType.folder,
          f,
          getItemName(f, BrowseItemType.folder),
          routes.BrowseController.index(bucket, f).url,
          folders.get(f).map(_.hidden).getOrElse(false)
        ))

      val assets = AssetModel.findByKeys(bucket, listResponse.assets).map(asset => (asset.key, asset)).toMap

      val assetItems = listResponse.assets.map(a =>
        BrowseItem(BrowseItemType.asset,
          a,
          getItemName(a, BrowseItemType.asset),
          bucketCloudfrontMap(bucket) + a,
          assets.get(a).map(_.hidden).getOrElse(false)
        )).groupBy(a => a.name.stripSuffix(".gz")).map {
          case (name, items) => {
            if (items.size == 1) {
              items
            } else {
              items.filter(_.name == name)
            }
          }
        }.flatten

      val items = (folderItems ++ assetItems).sortWith(_.name.toLowerCase < _.name.toLowerCase)

      Ok(views.html.browse.index(bucket, path, crumbs, items, marker, listResponse.nextMarker))
    }
  }

  /**
   * Returns detailed information on an asset, combining information stored by cashy and amazon s3
   * Result is in json format
   */
  def assetInfo(bucket: String, key: String) = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>

      // First get the cashy asset if it exists
      val cashyAsset = AssetModel.findByKey(bucket, key)

      val email = cashyAsset match {
        case Some(asset) => {
          UserModel.findById(asset.userId) match {
            case Some(user) => user.email
            case None => "???"
          }
        }
        case None => "???"
      }
      val created = cashyAsset.map(_.created).getOrElse("???").toString
      val headers = getAssetHeaders(bucket, key)
      val eTag = Option(headers.get("ETag").replaceAll("^\"|\"$", "")).getOrElse("???")
      val contentLength = Option(headers.get("Content-Length")).getOrElse("???")
      val cacheControl = Option(headers.get("Cache-Control")).getOrElse("???")
      val contentType = Option(headers.get("Content-Type")).getOrElse("???")
      val gzipped = S3Client.existsInS3(bucket, key + ".gz")

      val item = BrowseItemDetail(
        key,
        getItemName(key, BrowseItemType.asset),
        created,
        contentLength,
        contentType,
        fullS3AccessUrl + bucket + "/" + key,
        bucketCloudfrontMap(bucket) + key,
        email,
        cacheControl,
        eTag,
        gzipped,
        cashyAsset.map(_.hidden).getOrElse(false)
      )

      Ok(Json.stringify(Json.toJson(item))).withHeaders("Content-Type" -> "application/json")
    }
  }

  /**
   * Creates a folder in the bucket.  Returns json with an error if the bucket already exists or could
   * not be created
   */
  def createFolder(bucket: String, key: String) = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      try {
        // Make sure the key ends with a /
        val folderKey = if (key.endsWith("/")) key else key + "/"

        // Check if the folder exists
        if (S3Client.existsInS3(bucket, folderKey)) {
          throw new Exception("Folder already exists")
        }

        // Make sure its a valid folder name
        if (folderKey.contains("./")) {
          throw new Exception("Must not contain ./")
        }

        if (folderKey.startsWith("/")) {
          throw new Exception("Must not start with /")
        }

        if (folderKey.isEmpty()) {
          throw new Exception("Enter a name")
        }

        S3Client.createFolder(bucket, folderKey)
        FolderModel.createFolder(bucket, folderKey, false)
        Ok
      }
      catch {
        case e: Exception => {
          Logger.error("Exception when creating folder", e)
          val json = Json.stringify(Json.toJson(Map("error" -> e.getMessage)))
          Ok(json).withHeaders("Content-Type" -> "application/json")
        }
      }
    }
  }

  /**
   * Marks a hidden asset or folder as not hidden
   */
  def show(bucket: String, key: String) = updateHidden(bucket, key, false)

  /**
   * Marks a shown asset or folder as hidden
   */
  def hide(bucket: String, key: String) = updateHidden(bucket, key, true)

  /**
   * Updates the hidden value of an asset or folder
   */
  private def updateHidden(bucket: String, key: String, hidden: Boolean) = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      try {
        if (key.endsWith("/")) {
          // It is a folder
          FolderModel.findByKey(bucket, key) match {
            case Some(folder) => FolderModel.updateHidden(folder.bucket, folder.key, hidden)
            case None => FolderModel.createFolder(bucket, key, hidden)
          }
        } else {
          AssetModel.findByKey(bucket, key) match {
            case Some(asset) => AssetModel.updateHidden(asset.id, hidden)
            case None => throw new Exception("Asset does not exist")
          }
        }
        Ok
      }
      catch {
        case e: Exception => {
          Logger.error("Exception when hiding item", e)
          val json = Json.stringify(Json.toJson(Map("error" -> e.getMessage)))
          Ok(json).withHeaders("Content-Type" -> "application/json")
        }
      }
    }
  }

  /**
   * Splits a path e.g. root/folder1/ into a map of folder name and complete paths
   * e.g. root -> root/, folder -> root/folder1/
   */
  private def breadcrumbs(path: String): List[Tuple2[String,String]] = {
    val crumbs = path.split("/")
    crumbs.zipWithIndex.map { case (crumb,idx) =>
      (crumb, crumbs.slice(0,idx+1).mkString("/")+"/")
    }.toList
  }

  /**
   * Returns an item name from its full key. If it is a folder, add a trailing slash
   */
  private def getItemName(key: String, itemType: BrowseItemType.Value): String = {
    itemType match {
      case BrowseItemType.folder => {
        val noTrailingSlash = if (key.endsWith("/")) key.substring(0, key.length-1) else key
        noTrailingSlash.substring(noTrailingSlash.lastIndexOf("/")+1) + "/"
      }
      case BrowseItemType.asset => {
        key.substring(key.lastIndexOf("/")+1)
      }
    }
  }

  /**
   * Make a HEAD request to a publicly exposed asset to get information
   */
  private def getAssetHeaders(bucket: String, key: String): Option[Map[String,String]] = {
    val s3Url = fullS3AccessUrl + bucket + "/" + key

    val httpClient = HttpClientBuilder.create().build()
    val response = httpClient.execute(new HttpHead(s3Url))

    // Get the headers
    val headerMapOption = if (response.getStatusLine().getStatusCode() != 200) {
      None
    } else {
      val headerMap = response.getAllHeaders().map(header => (header.getName() -> header.getValue())).toMap
      Some(headerMap)
    }
    headerMapOption
  }

}

