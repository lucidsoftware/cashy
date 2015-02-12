package com.lucidchart.open.cashy.amazons3

import com.lucidchart.open.cashy.models.{AssetModel, AuditModel}
import com.lucidchart.open.cashy.config.CloudfrontConfig
import com.lucidchart.open.cashy.utils.{Mailer, MailerAddress, MailerMessage, KrakenClient}

import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import play.api.Play.{configuration, current}
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import java.util.Date
import play.api.libs.concurrent.Execution.Implicits.defaultContext

case class S3SyncAsset(
  key: String,
  date: Date
)

object S3Sync extends S3Sync
class S3Sync extends CloudfrontConfig {

  val akkaCashySystem: ActorSystem =  ActorSystem("cashysystem", configuration.underlying.getConfig("akka.cashysystem"))
  private val buckets = bucketCloudfrontMap.keys.toList
  private val syncFrequency = configuration.getInt("amazon.s3.syncFrequency").get
  private val syncUserId = 1 // hard-coded becauase the syncuser is part of evolutions
  private val alertEmail = configuration.getString("mailer.alertEmail").get
  private val fromEmail = configuration.getString("mailer.fromEmail").get
  private val tempUploadPrefix = configuration.getString("amazon.s3.tempUploadPrefix").get
  private val krakenEnabled = configuration.getBoolean("kraken.enabled").getOrElse(false)

  def schedule() {
    val assetSync = akkaCashySystem.actorOf(Props(new AssetSynchronizer(this)))
    akkaCashySystem.scheduler.schedule(
      0.seconds, syncFrequency.seconds, assetSync, "sync"
    )(defaultContext)
  }

  /**
   * Checks the data in amazon s3 against our asset database, updating the asset db as necessary
   */
  private def sync() {

    buckets.map { bucket =>
      val allS3Assets = S3Client.listAllObjects(bucket)

      deleteTempAssets(bucket, allS3Assets) // Do this first or else they will get uploaded and then deleted every sync
      checkChangedAssets(bucket, allS3Assets)
      checkS3GzAssets(bucket, allS3Assets)
    }

    if (krakenEnabled) {
      checkKrakenQuota()
    }
  }

  private def checkKrakenQuota() {
    val usageThreshold = configuration.getDouble("kraken.usageAlertThreshold").get
    val usage = KrakenClient.checkQuota()
    if (!usage.isDefined || usage.get > usageThreshold) {
      // Send an email to alert
      val fromAddress = MailerAddress(fromEmail)
      val toAddress = MailerAddress(alertEmail)

      val emailText = s"Kraken quota is at ${usage.get}.  Consider upgrading the plan temporarily or disabling kraken until it cycles"

      val message = MailerMessage(
        fromAddress,
        None,
        List(toAddress),
        Nil,
        Nil,
        "[Cashy] Kraken quota almost reached",
        emailText
      )

      Mailer.send(message)
    }
  }

  private def deleteTempAssets(bucket: String, allS3Assets: List[S3SyncAsset]) {
    val keysToDelete = allS3Assets.filter(asset => asset.key.startsWith(tempUploadPrefix)).map(_.key)

    keysToDelete.foreach { key =>
      S3Client.removeFromS3(bucket, key)
    }

  }

  private def checkChangedAssets(bucket: String, allS3Assets: List[S3SyncAsset]) {
    // Check to see what's missing from cashy, and what needs to be deleted from cashy
    val (assetsToAdd, assetsToDelete) = AssetModel.getChangedAssets(bucket, allS3Assets.map(_.key))

    // Filter the s3 sync assets
    val s3AddAssets = allS3Assets.filter(a => assetsToAdd.contains(a.key) && !a.key.endsWith(".gz"))

    assetsToDelete.foreach { asset =>
      AssetModel.deleteAsset(asset.id)
      AuditModel.createDeleteAudit(syncUserId, asset.bucket, asset.key, asset.link, asset.key.endsWith(".gz"))
    }

    s3AddAssets.foreach { s3Asset =>
      AssetModel.createAsset(bucket, s3Asset.key, syncUserId, s3Asset.date)
      val hasGzip = allS3Assets.exists(a => a.key == s3Asset.key+".gz")
      AuditModel.createUploadAudit(syncUserId, bucket, s3Asset.key, bucketCloudfrontMap(bucket) + s3Asset.key, hasGzip)
    }
  }

  private def checkS3GzAssets(bucket: String, allS3Assets: List[S3SyncAsset]) {
    // Check to make sure every .gz version in s3 has a non .gz version
    val s3GzOnly = allS3Assets.groupBy(a => a.key.stripSuffix(".gz")).map {
      case (name, items) => {
        if (items.size == 1) {
          if(items(0).key.endsWith(".gz")) {
            items
          } else {
            List()
          }
        } else {
          List()
        }
      }
    }.flatten

    if (!s3GzOnly.isEmpty) {

      // Send an email with the list of offending files
      val fromAddress = MailerAddress(fromEmail)
      val toAddress = MailerAddress(alertEmail)

      val emailText = "The following .gz files are in Amazon S3 bucket " + bucket + " and do not have a matching non-gz file:\n" +
        s3GzOnly.map("\t" + _.key).mkString("\n")

      val message = MailerMessage(
        fromAddress,
        None,
        List(toAddress),
        Nil,
        Nil,
        "[Cashy] Amazon S3 Gzip Inconsistency",
        emailText
      )

      Mailer.send(message)
    }

  }

  /**
   * A simple Actor that synchronizes cashy's database with S3
   */
  private class AssetSynchronizer(synchronizer: S3Sync) extends Actor {
    def receive = {
      case "sync" => {
        synchronizer.sync()
      }
    }
  }
}