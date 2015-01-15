package com.lucidchart.open.cashy.models

import java.util.Date
import play.api.Play.current
import play.api.db._
import com.lucidchart.open.relate.interp._

case class Asset(
  id: Long,
  bucket: String,
  key: String,
  userId: Long,
  created: Date
)

object AssetModel extends AssetModel
class AssetModel {

  def findById(assetId: Long): Option[Asset] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE `id` = $assetId""".asSingleOption { row =>
          Asset(row.long("id"), row.string("bucket"), row.string("key"), row.long("user_id"), row.date("created"))
      }
    }
  }

  def createAsset(bucket: String, key: String, userId: Long) {
    val now = new Date()
    DB.withConnection { implicit connection =>
      val assetId = sql"""INSERT INTO `assets`
        (`bucket`, `key`, `user_id`, `created`)
        VALUES ($bucket, $key, $userId, $now)""".executeInsertLong()
    }
  }

}