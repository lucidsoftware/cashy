package com.lucidchart.open.cashy.models

import com.lucidchart.open.relate.interp._
import com.lucidchart.open.relate.SqlResult
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
        WHERE `key` = $key AND `bucket` = $bucketName""".asSingleOption(folderParser)
    }
  }

  def findByKeys(bucketName: String, keys: List[String]): List[Folder] = {
    if (keys.isEmpty) {
      Nil
    } else {
      DB.withConnection { implicit connection =>
        sql"""SELECT `bucket`, `key`, `created`, `hidden`
          FROM `folders`
          WHERE `key` IN ($keys) AND `bucket` = $bucketName""".asList(folderParser)
      }
    }
  }

  def updateHidden(bucket: String, key: String, hidden: Boolean) {
    DB.withConnection { implicit connection =>
      sql"""UPDATE `folders` SET `hidden` = $hidden WHERE `bucket` = $bucket AND `key` = $key""".executeUpdate()
    }
  }

  def createFolder(bucket: String, key: String, hidden: Boolean) {
    DB.withConnection { implicit connection =>
      sql"""INSERT INTO `folders`
        (`bucket`, `key`, `created`, `hidden`)
        VALUES ($bucket, $key, ${new Date}, $hidden)""".execute()
    }
  }

}
