package com.lucidchart.open.cashy.models

import com.lucidchart.open.cashy.config.CloudfrontConfig
import com.lucidchart.open.relate.interp._
import com.lucidchart.open.relate.SqlResult
import java.util.Date
import play.api.db._
import play.api.Play.{configuration, current}

case class Asset(
  id: Long,
  bucket: String,
  key: String,
  userId: Long,
  created: Date
) extends CloudfrontConfig {
  /**
   * Return a link to this Asset in Cloudfront.
   *
   * @return the link to the Asset
   */
  def link: String = {
    bucketCloudfrontMap(bucket) + key
  }
}

object AssetModel extends AssetModel
class AssetModel {
  private val searchMax = configuration.getInt("search.max").get
  private val assetParser = { row: SqlResult =>
    Asset(
      row.long("id"),
      row.string("bucket"),
      row.string("key"),
      row.long("user_id"),
      row.date("created")
    )
  }

  def findById(assetId: Long): Option[Asset] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE `id` = $assetId""".asSingleOption(assetParser)
    }
  }

  def findByKey(bucketName: String, key: String): Option[Asset] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE `key` = $key AND `bucket` = $bucketName""".asSingleOption { row =>
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

  /**
   * Search for assets that match a query.
   *
   * If % characters are included in the query, it is assumed that the user knows what they are
   * doing and the query is used as is. If % is not present in the query, they are appended on
   * either side of the search term.
   *
   * @param query the query string to look for
   * @return a List of Assets that match the query. The maximum size of this list is configurable
   * using the key "search.max" in application.conf
   */
  def search(query: String): List[Asset] = {
    val searchTerm = if (query.contains("%")) query else "%" + query + "%"

    DB.withConnection { implicit connection =>
      sql"""
        SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE `key` LIKE $searchTerm
        LIMIT $searchMax
      """.asList(assetParser)
    }
  }
}
