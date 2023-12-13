package com.lucidchart.open.cashy.models

import com.lucidchart.open.cashy.config.Buckets
import com.lucidchart.relate._
import java.time.Instant
import javax.inject.Inject
import org.apache.commons.codec.digest.DigestUtils
import play.api.db.Database
import play.api.Configuration

case class Asset(
    link: String,
    bucket: String,
    key: String,
    userId: Long,
    created: Instant,
    hidden: Boolean
) {
  def parent: String = {
    key.substring(0, key.lastIndexOf("/") + 1)
  }
}

class AssetModel @Inject() (buckets: Buckets, configuration: Configuration, db: Database) {
  private val searchMax = configuration.get[Int]("search.max")
  private val assetParser = { row: SqlRow =>
    val bucket = row.string("bucket")
    val key = row.string("key")
    Asset(
      buckets.publicUrl(bucket) + key,
      bucket,
      key,
      row.long("user_id"),
      row.instant("created"),
      row.bool("hidden")
    )
  }

  def findByKey(bucketName: String, key: String): Option[Asset] = {
    db.withConnection { implicit connection =>
      sql"""SELECT `bucket`, `key`, `user_id`, `created`, `hidden`
        FROM `assets`
      WHERE `key_hash` = ${getHash(key)} AND `bucket_hash` = ${getHash(bucketName)}"""
        .asSingleOption(assetParser)
    }
  }

  def findByKeys(bucketName: String, keys: List[String]): List[Asset] = {
    if (keys.isEmpty) {
      Nil
    } else {
      val keyHashes = keys.map(getHash(_))
      db.withConnection { implicit connection =>
        sql"""SELECT `bucket`, `key`, `user_id`, `created`, `hidden`
          FROM `assets`
          WHERE `key_hash` IN ($keyHashes) AND `bucket_hash` = ${getHash(bucketName)}""".asList(assetParser)
      }
    }
  }

  def createAsset(bucket: String, key: String, userId: Long): Unit = {
    createAsset(bucket, key, userId, Instant.now())
  }

  def createAsset(bucket: String, key: String, userId: Long, date: Instant): Unit = {
    db.withConnection { implicit connection =>
      sql"""INSERT INTO `assets`
        (`bucket`, `key`, `user_id`, `created`, `bucket_hash`, `key_hash`)
        VALUES ($bucket, $key, $userId, $date, ${getHash(bucket)}, ${getHash(key)})""".execute()
    }
  }

  def updateHidden(bucket: String, key: String, hidden: Boolean): Unit = {
    db.withConnection { implicit connection =>
      sql"""UPDATE `assets` SET `hidden` = $hidden WHERE `bucket_hash` = ${getHash(
        bucket
      )} AND `key_hash` = ${getHash(
        key
      )}""".executeUpdate()
    }
  }

  def deleteAsset(bucket: String, key: String): Unit = {
    db.withConnection { implicit connection =>
      sql"""DELETE FROM `assets`
        WHERE `bucket_hash` = ${getHash(bucket)} AND `key_hash` = ${getHash(key)}""".execute()
    }
  }

  /**
    * Case-Insensitive Search for assets that match a query.
    *
   * If % characters are included in the query, it is assumed that the user knows what they are
    * doing and the query is used as is. If % is not present in the query, they are appended on
    * either side of the search term.  By doing this we definitely do not use any index.
    *
   * @param query the query string to look for
    * @return a List of Assets that match the query. The maximum size of this list is configurable
    * using the key "search.max" in application.conf
    */
  def search(rawQuery: String): List[Asset] = {
    val query = rawQuery.replace("_", """\_""")
    val searchTerm = if (query.contains("%")) query.toLowerCase else "%" + query.toLowerCase + "%"

    db.withConnection { implicit connection =>
      sql"""
        SELECT `bucket`, `key`, `user_id`, `created`, `hidden`
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
    db.withConnection { implicit connection =>
      val assets = sql"""
        SELECT `bucket`, `key`, `user_id`, `created`, `hidden`
        FROM `assets`
        WHERE `bucket_hash` = ${getHash(bucket)}
        ORDER BY `key` ASC
      """.asIterator(assetParser)

      val assetsToDelete = List.newBuilder[Asset]
      val assetsToAdd = List.newBuilder[String]
      val s3Iter = s3Keys.iterator

      // consume the stream of assets
      assets.foreach { asset =>
        // If the asset key is not in s3keys then it should be deleted
        if (!s3Keys.contains(asset.key)) {
          assetsToDelete += asset
        } else {
          // Iterate through the s3 keys until we catch up with the cashy assets
          // any item that was skipped needs to be added to cashy
          var s3Key = s3Iter.next()
          while (s3Key != asset.key) {
            assetsToAdd += s3Key
            s3Key = s3Iter.next()
          }
        }
      }

      // If there are still things in the iterator they need to be added
      while (s3Iter.hasNext) {
        assetsToAdd += s3Iter.next()
      }

      (assetsToAdd.result(), assetsToDelete.result())
    }
  }

  /**
    * Converts a string into a long value representing the first 8 bytes of its md5
    * @param data the data to get the md5 hash of
    * @return the string with the first 8 bytes of md5
    */
  private def getHash(data: String): String = {
    DigestUtils.md5Hex(data).take(8)
  }
}
