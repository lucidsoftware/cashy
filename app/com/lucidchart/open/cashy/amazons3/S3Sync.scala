package com.lucidchart.open.cashy.amazons3

import com.google.inject.AbstractModule

import com.lucidchart.open.cashy.models.{AssetModel, AuditModel}
import com.lucidchart.open.cashy.config.Buckets
import com.lucidchart.open.cashy.utils.{Mailer, MailerAddress, MailerMessage}

import akka.actor.{Actor, ActorSystem, Props}
import java.time.Instant
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class S3SyncAsset(
  key: String,
  date: Instant,
)

@Singleton
class S3Sync @Inject() (
  assetModel: AssetModel,
  auditModel: AuditModel,
  mailer: Mailer,
  s3Client: S3Client,
  buckets: Buckets,
  configuration: Configuration,
)(implicit ec: ExecutionContext) {

  val akkaCashySystem: ActorSystem =
    ActorSystem("cashysystem", configuration.underlying.getConfig("akka.cashysystem"))
  private val syncFrequency = configuration.get[Int]("amazon.s3.syncFrequency")
  private val syncUserId = 1 // hard-coded becauase the syncuser is part of evolutions
  private val alertEmail = configuration.get[String]("mailer.alertEmail")
  private val fromEmail = configuration.get[String]("mailer.fromEmail")
  private val tempUploadPrefix = configuration.get[String]("amazon.s3.tempUploadPrefix")

  private[this] def schedule(): Unit = {
    val assetSync = akkaCashySystem.actorOf(Props(new AssetSynchronizer(this)))
    akkaCashySystem.scheduler.scheduleWithFixedDelay(
      0.seconds,
      syncFrequency.seconds,
      assetSync,
      "sync",
    )(ec)
  }

  schedule() // schedule once it is created

  /**
   * Checks the data in amazon s3 against our asset database, updating the asset db as necessary
   */
  private def sync(): Unit = {

    buckets.names.map { bucket =>
      val allS3Assets = s3Client.listAllObjects(bucket).toList

      val nonTempAssets =
        deleteTempAssets(
          bucket,
          allS3Assets,
        ) // Do this first or else they will get uploaded and then deleted every sync
      checkChangedAssets(bucket, nonTempAssets)
      checkS3GzAssets(bucket, nonTempAssets)
    }
  }

  // Deletes all the assets from S3 that are in the temp directory.  Returns the list of assets that were not deleted
  private def deleteTempAssets(bucket: String, allS3Assets: List[S3SyncAsset]): List[S3SyncAsset] = {
    val keysToDelete = allS3Assets.filter(asset => asset.key.startsWith(tempUploadPrefix)).map(_.key)

    keysToDelete.foreach { key =>
      s3Client.removeFromS3(bucket, key)
    }
    allS3Assets.filter(asset => !asset.key.startsWith(tempUploadPrefix))
  }

  private def checkChangedAssets(bucket: String, allS3Assets: List[S3SyncAsset]): Unit = {
    // Check to see what's missing from cashy, and what needs to be deleted from cashy
    val (assetsToAdd, assetsToDelete) = assetModel.getChangedAssets(bucket, allS3Assets.map(_.key))

    // Filter the s3 sync assets
    val s3AddAssets = allS3Assets.filter(a => assetsToAdd.contains(a.key) && !a.key.endsWith(".gz"))

    assetsToDelete.foreach { asset =>
      assetModel.deleteAsset(asset.bucket, asset.key)
      auditModel.createDeleteAudit(syncUserId, asset.bucket, asset.key, asset.link, asset.key.endsWith(".gz"))
    }

    s3AddAssets.foreach { s3Asset =>
      assetModel.createAsset(bucket, s3Asset.key, syncUserId, s3Asset.date)
      val hasGzip = allS3Assets.exists(a => a.key == s3Asset.key + ".gz")
      auditModel.createUploadAudit(
        syncUserId,
        bucket,
        s3Asset.key,
        buckets.cloudfrontUrl(bucket) + s3Asset.key,
        hasGzip,
      )
    }
  }

  private def checkS3GzAssets(bucket: String, allS3Assets: List[S3SyncAsset]): Unit = {
    // Check to make sure every .gz version in s3 has a non .gz version
    val s3GzOnly = allS3Assets
      .groupBy(a => a.key.stripSuffix(".gz"))
      .collect {
        case (name, List(item)) if item.key.endsWith(".gz") => item
      }

    if (!s3GzOnly.isEmpty) {

      // Send an email with the list of offending files
      val fromAddress = MailerAddress(fromEmail)
      val toAddress = MailerAddress(alertEmail)

      val emailText =
        "The following .gz files are in Amazon S3 bucket " + bucket + " and do not have a matching non-gz file:\n" +
          s3GzOnly.map("\t" + _.key).mkString("\n")

      val message = MailerMessage(
        fromAddress,
        None,
        List(toAddress),
        Nil,
        Nil,
        "[Cashy] Amazon S3 Gzip Inconsistency",
        emailText,
      )

      mailer.send(message)
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

class S3SyncModule extends AbstractModule {
  override def configure() = {
    bind(classOf[S3Sync]).asEagerSingleton()
  }
}
