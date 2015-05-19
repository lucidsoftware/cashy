package com.lucidchart.open.cashy.models

import com.lucidchart.open.relate.interp._
import com.lucidchart.open.relate.SqlResult
import org.apache.commons.codec.digest.DigestUtils
import java.util.Date
import play.api.db._
import play.api.Play.current

case class Folder(
  bucket: String,
  key: String,
  created: Date,
  hidden: Boolean
)

object FolderModel extends FolderModel
class FolderModel {
  private val folderParser = { row: SqlResult =>
    Folder(
      row.string("bucket"),
      row.string("key"),
      row.date("created"),
      row.bool("hidden")
    )
  }

  def findByKey(bucketName: String, key: String): Option[Folder] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `bucket`, `key`, `created`, `hidden`
        FROM `folders`
        WHERE `key_hash` = ${getHash(key)} AND `bucket_hash` = ${getHash(bucketName)}""".asSingleOption(folderParser)
    }
  }

  def findByKeys(bucketName: String, keys: List[String]): List[Folder] = {
    if (keys.isEmpty) {
      Nil
    } else {
      val keyHashes = keys.map(getHash(_))
      DB.withConnection { implicit connection =>
        sql"""SELECT `bucket`, `key`, `created`, `hidden`
          FROM `folders`
          WHERE `key_hash` IN ($keyHashes) AND `bucket_hash` = ${getHash(bucketName)}""".asList(folderParser)
      }
    }
  }

  def updateHidden(bucket: String, key: String, hidden: Boolean) {
    DB.withConnection { implicit connection =>
      sql"""UPDATE `folders` SET `hidden` = $hidden WHERE `bucket_hash` = ${getHash(bucket)} AND `key_hash` = ${getHash(key)}""".executeUpdate()
    }
  }

  def createFolder(bucket: String, key: String, hidden: Boolean) {
    DB.withConnection { implicit connection =>
      sql"""INSERT INTO `folders`
        (`bucket`, `key`, `created`, `hidden`, `bucket_hash`, `key_hash`)
        VALUES ($bucket, $key, ${new Date}, $hidden, ${getHash(bucket)}, ${getHash(key)})""".execute()
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
