package com.lucidchart.open.cashy.uploaders

import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.utils.GzipHelper
import com.lucidchart.open.cashy.config.ExtensionsConfig
import com.lucidchart.open.cashy.models.{AssetModel, AuditModel, Asset, User}

import play.api.Play.current
import play.api.Play.configuration

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

trait Uploader extends ExtensionsConfig {
  val minGzipSavings: Double = configuration.getDouble("upload.minGzipSavings").get

  // Check if gzip compression saves enough to meet the threshold and uploads to s3 if so
  protected def gzipAndUpload(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String]): Boolean = {
    val compressedBytes = GzipHelper.compress(bytes)
    val gzipSavings = (bytes.length - compressedBytes.length) / bytes.length.toDouble
    if (gzipSavings >= minGzipSavings) {
      if(!S3Client.uploadToS3(bucket, assetName, compressedBytes, contentType, gzipped=true)) {
        throw new UploadFailedException("Uploading gzip version for file failed: " + (bucket, assetName))
      }
      true
    } else {
      false
    }
  }

  // Upload the asset and its gzip version
  protected def uploadAndAudit(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String], user: User): Asset = {
    val gzipUploaded = gzipAndUpload(bytes, bucket, assetName, contentType)

    // Try to upload the asset to S3
    if(S3Client.uploadToS3(bucket, assetName, bytes, contentType)) {
      AssetModel.createAsset(bucket, assetName, user.id)
      val asset = AssetModel.findByKey(bucket, assetName).get

      AuditModel.createUploadAudit(user.id, bucket, assetName, asset.link, gzipUploaded)
      asset
    } else {
      // If gzip upload happened but this one failed, have to delete the gzip one
      if (gzipUploaded) {
        try {
          S3Client.removeFromS3(bucket, assetName, gzipped=true)
        }
        catch {
          case e: Exception => {
            throw new UploadFailedException("Uploading non-gzip version for file failed and could not delete the gzip version.  Please contact your S3 administrator to correct the issue: " + (bucket, assetName))
          }
        }
      }
      throw new UploadFailedException("Uploading non-gzip version for file failed: " + (bucket, assetName))
    }
  }

  def upload(bytes: Array[Byte], contentType: Option[String], user: User, data: UploadFormSubmission): UploadResult
}