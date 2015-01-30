package com.lucidchart.open.cashy.models

import com.lucidchart.open.cashy.config.CloudfrontConfig
import com.lucidchart.open.relate.interp._
import com.lucidchart.open.relate.SqlResult
import java.util.Date
import play.api.db._
import play.api.Play.{configuration, current}
import scala.collection.mutable.MutableList

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
    createAsset(bucket, key, userId, now)
  }

  def createAsset(bucket: String, key: String, userId: Long, date: Date) {
    DB.withConnection { implicit connection =>
      val assetId = sql"""INSERT INTO `assets`
        (`bucket`, `key`, `user_id`, `created`)
        VALUES ($bucket, $key, $userId, $date)""".executeInsertLong()
    }
  }

  def deleteAsset(id: Long) {
    DB.withConnection { implicit connection =>
      sql"""DELETE FROM `assets`
        WHERE `id` = $id""".execute()
    }
  }

  /**
   * Case-Insensitive Search for assets that match a query.
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
    val searchTerm = if (query.contains("%")) query.toLowerCase else "%" + query.toLowerCase + "%"

    DB.withConnection { implicit connection =>
      sql"""
        SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE LOWER(`key`) LIKE $searchTerm
        LIMIT $searchMax
      """.asList(assetParser)
    }
  }

  /**
   * Compare assets in the database to a list of s3 keys
   *
   * S3 returns assets in case-sensitive order by key.  By travering our assets by key,
   * case-sensitive, we can compare it to the list of keys from S3 to see which are missing
   * and which need to be deleted.
   *
   * @param bucket the bucket to filter assets by
   * @param s3Keys a list of keys for the bucket from s3
   * @return a tuple that has a list of assets that need to be added to cashy,
   *         and a list of assets that need to be deleted from cashy
   */
  def getChangedAssets(bucket: String, s3Keys: List[String]): Tuple2[List[String], List[Asset]] = {
    DB.withConnection { implicit connection =>
      val assets = sql"""
        SELECT `id`, `bucket`, `key`, `user_id`, `created`
        FROM `assets`
        WHERE `bucket` = $bucket
        ORDER BY `key` ASC
      """.asIterator(assetParser)

      val assetsToDelete: MutableList[Asset] = MutableList()
      val assetsToAdd: MutableList[String] = MutableList()
      val s3Iter = s3Keys.toIterator

      // consume the stream of assets
      assets.foreach { asset =>
        // If the asset key is not in s3keys then it should be deleted
        if (!s3Keys.contains(asset.key)) {
          assetsToDelete += asset
        } else {
          // Iterate through the s3 keys until we catch up with the cashy assets
          // any item that was skipped needs to be added to cashy
          var s3Key = s3Iter.next
          while(s3Key != asset.key) {
            assetsToAdd += s3Key
            s3Key = s3Iter.next
          }
        }
      }

      // If there are stil things in the iterator they need to be added
      while(s3Iter.hasNext) {
        assetsToAdd += s3Iter.next
      }

      (assetsToAdd.toList, assetsToDelete.toList)
    }
  }
}
