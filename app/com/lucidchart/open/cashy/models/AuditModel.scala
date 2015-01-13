package com.lucidchart.open.cashy.models

import java.util.Date
import play.api.Play.current
import play.api.db._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import com.lucidchart.open.relate.interp._

object AuditType extends Enumeration {
  val upload  = Value(0, "UPLOAD")
}

case class AuditEntry(
  id: Long,
  user_id: Long,
  data: String,
  auditType: AuditType.Value,
  created: Date
)

case class UploadAuditData(
  bucket: String,
  assetKey: String,
  cloudfrontUrl: String,
  gzipped: Boolean
)
object UploadAuditData {
  implicit val uploadDataReads: Reads[UploadAuditData] = (
    (JsPath \ "bucket").read[String] and
    (JsPath \ "assetKey").read[String] and
    (JsPath \ "cloudfrontUrl").read[String] and
    (JsPath \ "gzipped").read[Boolean])(UploadAuditData.apply _)

  implicit val uploadDataWrites: Writes[UploadAuditData] = (
    (JsPath \ "bucket").write[String] and
    (JsPath \ "assetKey").write[String] and
    (JsPath \ "cloudfrontUrl").write[String] and
    (JsPath \ "gzipped").write[Boolean])(unlift(UploadAuditData.unapply))
}

object AuditModel extends AuditModel
class AuditModel {

  private def findById(auditId: Long): Option[AuditEntry] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `user_id`, `data`, `type`, `created`
        FROM `audits`
        WHERE `id` = $auditId""".asSingleOption { row =>
          AuditEntry(row.long("id"), row.long("user_id"), row.string("data"), AuditType(row.int("type")), row.date("created"))
      }
    }
  }

  private def createAuditEntry(userId: Long, data: String, assetType: AuditType.Value) = {
    val now = new Date()
    DB.withConnection { implicit connection =>
      val auditId = sql"""INSERT INTO `audits`
        (`user_id`, `data`, `type`, `created`)
        VALUES ($userId, $data, ${assetType.id}, $now)""".executeInsertLong()
      findById(auditId).getOrElse {
        throw new Exception("Mysql insert failed")
      }
    }
  }

  def createUploadAudit(userId: Long, bucket: String, assetKey: String, cloudfrontUrl: String, gzipped: Boolean): AuditEntry = {
    val uploadData = Json.stringify(Json.toJson(UploadAuditData(bucket, assetKey, cloudfrontUrl, gzipped)))
    createAuditEntry(userId, uploadData, AuditType.upload)
  }

}