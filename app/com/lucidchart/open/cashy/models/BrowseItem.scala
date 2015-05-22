package com.lucidchart.open.cashy.models

import java.util.Date
import play.api.libs.json._
import play.api.libs.functional.syntax._

object BrowseItemType extends Enumeration {
  val folder  = Value(0, "FOLDER")
  val asset  = Value(1, "ASSET")
}

case class BrowseItemDetail(
  key: String,
  name: String,
  creationDate: String,
  size: String,
  contentType: String,
  s3Url: String,
  cloudfrontUrl: String,
  creator: String,
  cacheControl: String,
  eTag: String,
  gzipped: Boolean,
  hidden: Boolean
)
object BrowseItemDetail {
  implicit val uploadDataWrites: Writes[BrowseItemDetail] = (
    (JsPath \ "key").write[String] and
    (JsPath \ "name").write[String] and
    (JsPath \ "creationDate").write[String] and
    (JsPath \ "size").write[String] and
    (JsPath \ "contentType").write[String] and
    (JsPath \ "s3Url").write[String] and
    (JsPath \ "cloudfrontUrl").write[String] and
    (JsPath \ "creator").write[String] and
    (JsPath \ "cacheControl").write[String] and
    (JsPath \ "eTag").write[String] and
    (JsPath \ "gzipped").write[Boolean] and
    (JsPath \ "hidden").write[Boolean]
  )(unlift(BrowseItemDetail.unapply))

}

case class BrowseItem(
  itemType: BrowseItemType.Value,
  key: String,
  name: String,
  link: String,
  hidden: Boolean
)