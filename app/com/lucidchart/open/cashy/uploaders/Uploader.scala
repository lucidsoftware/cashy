package com.lucidchart.open.cashy.uploaders

import javax.inject.Inject
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.utils.GzipHelper
import com.lucidchart.open.cashy.config.{ExtensionsConfig, UploadFeatures}
import com.lucidchart.open.cashy.models.{AssetModel, AuditModel, Asset, User}

import play.api.Configuration

case class UploadFailedException(message: String) extends Exception(message)

case class UploadResult(
    uploadedAssets: List[Tuple2[String, Asset]],
    existingAssets: List[Tuple2[String, Asset]],
    bucket: String,
    parent: String
)

case class UploadFormSubmission(
    bucket: String,
    assetName: String,
    resizeImage: Boolean = false,
    resizedImage: Option[String] = None,
    uploadRetina: Boolean = false,
    assetRetinaName: Option[String] = None,
    imageWidth: Option[Int] = None,
    imageHeight: Option[Int] = None,
    assetURL: Option[String] = None
)

class UploaderComponents @Inject() (
    val configuration: Configuration,
    val extensionsConfig: ExtensionsConfig,
    val uploadFeatures: UploadFeatures,
    val s3Client: S3Client,
    val assetModel: AssetModel,
    val auditModel: AuditModel
)

abstract class Uploader(components: UploaderComponents) {
  protected val configuration = components.configuration
  protected val extensionsConfig = components.extensionsConfig
  protected val uploadFeatures = components.uploadFeatures
  protected val s3Client = components.s3Client
  protected val assetModel = components.assetModel
  protected val auditModel = components.auditModel

  val minGzipSavings: Double = configuration.get[Double]("upload.minGzipSavings")

  // Check if gzip compression saves enough to meet the threshold and uploads to s3 if so
  protected def gzipAndUpload(
      bytes: Array[Byte],
      bucket: String,
      assetName: String,
      contentType: Option[String]
  ): Boolean = {
    val compressedBytes = GzipHelper.compress(bytes)
    val gzipSavings = (bytes.length - compressedBytes.length) / bytes.length.toDouble
    if (gzipSavings >= minGzipSavings) {
      if (!s3Client.uploadToS3(bucket, assetName, compressedBytes, contentType, gzipped = true)) {
        throw new UploadFailedException("Uploading gzip version for file failed: " + (bucket, assetName))
      }
      true
    } else {
      false
    }
  }

  // Upload the asset and its gzip version
  protected def uploadAndAudit(
      bytes: Array[Byte],
      bucket: String,
      assetName: String,
      contentType: Option[String],
      user: User
  ): Asset = {
    val gzipUploaded = gzipAndUpload(bytes, bucket, assetName, contentType)

    // Try to upload the asset to S3
    if (s3Client.uploadToS3(bucket, assetName, bytes, contentType)) {
      assetModel.createAsset(bucket, assetName, user.id)
      val asset = assetModel.findByKey(bucket, assetName).get

      auditModel.createUploadAudit(user.id, bucket, assetName, asset.link, gzipUploaded)
      asset
    } else {
      // If gzip upload happened but this one failed, have to delete the gzip one
      if (gzipUploaded) {
        try {
          s3Client.removeFromS3(bucket, assetName, gzipped = true)
        } catch {
          case e: Exception => {
            throw new UploadFailedException(
              "Uploading non-gzip version for file failed and could not delete the gzip version.  Please contact your S3 administrator to correct the issue: " + (bucket, assetName)
            )
          }
        }
      }
      throw new UploadFailedException("Uploading non-gzip version for file failed: " + (bucket, assetName))
    }
  }

  def upload(
      bytes: Array[Byte],
      contentType: Option[String],
      user: User,
      data: UploadFormSubmission
  ): UploadResult
}
