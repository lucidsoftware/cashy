package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.config.Buckets
import com.lucidchart.open.cashy.models.{
  BrowseItem,
  BrowseItemType,
  BrowseItemDetail,
  AssetModel,
  UserModel,
  FolderModel
}
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.amazons3.ListObjectsResponse

import javax.inject.Inject
import play.api.Logger
import play.api.mvc.{Request, Action, ControllerComponents, AbstractController}
import play.api.libs.json.Json
import play.api.i18n.I18nSupport
import play.api.Configuration
import org.apache.http.client.methods.HttpHead
import org.apache.http.impl.client.HttpClientBuilder
import scala.io.Source

class BrowseController @Inject() (
    authAction: AuthAction,
    s3Client: S3Client,
    assetModel: AssetModel,
    folderModel: FolderModel,
    userModel: UserModel,
    buckets: Buckets,
    configuration: Configuration,
    components: ControllerComponents
) extends AbstractController(components)
    with I18nSupport {

  val logger = Logger(this.getClass)

  val fullS3AccessUrl = configuration.get[String]("amazon.s3.fullAccessUrl")

  /**
    * Browse page, authentication required
    */
  def index(bucket: String, path: String, marker: Option[String]) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        val crumbs = breadcrumbs(path)

        val browsePath = if (path.endsWith("/") || path.isEmpty) path else path + "/"

        val listResponse = s3Client.listObjects(bucket, browsePath, marker)

        val folders =
          folderModel.findByKeys(bucket, listResponse.folders).map(folder => (folder.key, folder)).toMap

        val folderItems = listResponse.folders.map(f =>
          BrowseItem(
            BrowseItemType.folder,
            f,
            getItemName(f, BrowseItemType.folder),
            routes.BrowseController.index(bucket, f).url,
            folders.get(f).map(_.hidden).getOrElse(false)
          )
        )

        val assets = assetModel.findByKeys(bucket, listResponse.assets).map(asset => (asset.key, asset)).toMap

        val assetItems = listResponse.assets
          .map(a =>
            BrowseItem(
              BrowseItemType.asset,
              a,
              getItemName(a, BrowseItemType.asset),
              buckets.cloudfrontUrl(bucket) + a,
              assets.get(a).map(_.hidden).getOrElse(false)
            )
          )
          .groupBy(a => a.name.stripSuffix(".gz"))
          .flatMap {
            case (name, items) => {
              if (items.size == 1) {
                items
              } else {
                items.filter(_.name == name)
              }
            }
          }

        val items = (folderItems ++ assetItems).sortWith(_.name.toLowerCase < _.name.toLowerCase)
        implicit val _buckets = buckets

        Ok(
          views.html.browse
            .index(bucket, path, crumbs, items, marker, listResponse.nextMarker)
        )
      }
    }

  /**
    * Returns detailed information on an asset, combining information stored by cashy and amazon s3
    * Result is in json format
    */
  def assetInfo(bucket: String, key: String) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        // First get the cashy asset if it exists
        val cashyAsset = assetModel.findByKey(bucket, key)

        val email = cashyAsset match {
          case Some(asset) => {
            userModel.findById(asset.userId) match {
              case Some(user) => user.email
              case None       => "???"
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
        val gzipped = s3Client.existsInS3(bucket, key + ".gz")

        val item = BrowseItemDetail(
          key,
          getItemName(key, BrowseItemType.asset),
          created,
          contentLength,
          contentType,
          fullS3AccessUrl + bucket + "/" + key,
          buckets.cloudfrontUrl(bucket) + key,
          email,
          cacheControl,
          eTag,
          gzipped,
          cashyAsset.map(_.hidden).getOrElse(false)
        )

        Ok(Json.toJson(item))
      }
    }

  /**
    * Creates a folder in the bucket.  Returns json with an error if the bucket already exists or could
    * not be created
    */
  def createFolder(bucket: String, key: String) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        try {
          // Make sure the key ends with a /
          val folderKey = if (key.endsWith("/")) key else key + "/"

          // Check if the folder exists
          if (s3Client.existsInS3(bucket, folderKey)) {
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

          s3Client.createFolder(bucket, folderKey)
          folderModel.createFolder(bucket, folderKey, false)
          Ok
        } catch {
          case e: Exception => {
            logger.error("Exception when creating folder", e)
            val json = Json.toJson(Map("error" -> e.getMessage))
            Ok(json)
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
  private def updateHidden(bucket: String, key: String, hidden: Boolean) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        try {
          if (key.endsWith("/")) {
            // It is a folder
            folderModel.findByKey(bucket, key) match {
              case Some(folder) => folderModel.updateHidden(folder.bucket, folder.key, hidden)
              case None         => folderModel.createFolder(bucket, key, hidden)
            }
          } else {
            assetModel.findByKey(bucket, key) match {
              case Some(asset) => assetModel.updateHidden(asset.bucket, asset.key, hidden)
              case None        => throw new Exception("Asset does not exist")
            }
          }
          Ok
        } catch {
          case e: Exception => {
            logger.error("Exception when hiding item", e)
            val json = Json.toJson(Map("error" -> e.getMessage))
            Ok(json)
          }
        }
      }
    }

  /**
    * Splits a path e.g. root/folder1/ into a map of folder name and complete paths
    * e.g. root -> root/, folder -> root/folder1/
    */
  private def breadcrumbs(path: String): List[Tuple2[String, String]] = {
    val crumbs = path.split("/")
    crumbs.zipWithIndex.map {
      case (crumb, idx) =>
        (crumb, crumbs.slice(0, idx + 1).mkString("/") + "/")
    }.toList
  }

  /**
    * Returns an item name from its full key. If it is a folder, add a trailing slash
    */
  private def getItemName(key: String, itemType: BrowseItemType.Value): String = {
    itemType match {
      case BrowseItemType.folder => {
        val noTrailingSlash = if (key.endsWith("/")) key.substring(0, key.length - 1) else key
        noTrailingSlash.substring(noTrailingSlash.lastIndexOf("/") + 1) + "/"
      }
      case BrowseItemType.asset => {
        key.substring(key.lastIndexOf("/") + 1)
      }
    }
  }

  /**
    * Make a HEAD request to a publicly exposed asset to get information
    */
  private def getAssetHeaders(bucket: String, key: String): Option[Map[String, String]] = {
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
