package com.lucidchart.open.cashy.uploaders

import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.models.{Asset, User, AssetModel}
import com.lucidchart.open.cashy.utils.{DownloadHelper, KrakenClient}

import scala.collection.mutable.MutableList

object KrakenImageUploader extends KrakenImageUploader
class KrakenImageUploader extends Uploader {

  override def upload(bytes: Array[Byte], contentType: Option[String], user: User, data: UploadFormSubmission): UploadResult = {
    val bucket = data.bucket
    val assetName = data.assetName
    val uploadedAssets = MutableList[Tuple2[String,Asset]]()
    val existingAssets = MutableList[Tuple2[String,Asset]]()

    val extension = getExtension(assetName)
    // If the image is resized
    val asset = if(data.resizeImage) {

      val resizeWidth = data.imageWidth.get
      val resizeHeight = data.imageHeight.get

      // Get the bytes from the already resized image (when the user previewed it)
      val resizedBytes = DownloadHelper.download(data.resizedImage.get).bytes

      // Upload it to S3
      val asset = uploadAndAudit(resizedBytes, bucket, assetName, contentType, user)
      uploadedAssets += ((resizeWidth + "x" + resizeHeight, asset))

      // If retina option is checked
      if(data.uploadRetina) {
        val retinaName = data.assetRetinaName.get

        // Check if @2x name is taken
        if(!S3Client.existsInS3(bucket, retinaName)) {
          val tempUrl = tempUpload(bucket, bytes, contentType, extension)

          // Send the resize request to Kraken
          val retinaWidth = resizeWidth * 2
          val retinaHeight = resizeHeight * 2
          val retinaBytes = KrakenClient.resizeImage(tempUrl, retinaWidth, retinaHeight)

          // Upload to S3
          val retinaAsset = uploadAndAudit(retinaBytes, bucket, retinaName, contentType, user)
          uploadedAssets += (("Retina", retinaAsset))

        } else {
          // Get that asset and return it
          val retinaAsset = AssetModel.findByKey(bucket, retinaName).get
          existingAssets += (("Retina", retinaAsset))
        }
      }
      asset
    } else {

      val tempUrl = tempUpload(bucket, bytes, contentType, extension)
      val compressedBytes = KrakenClient.compressImage(tempUrl)

      // upload to s3
      val asset = uploadAndAudit(compressedBytes, bucket, assetName, contentType, user)
      uploadedAssets += (("Original", asset))
      asset
    }

    UploadResult(
      uploadedAssets.toList,
      existingAssets.toList,
      asset.bucket,
      asset.parent
    )
  }

  // Uploads an object to the temp location with a random name
  private def tempUpload(bucket: String, bytes: Array[Byte], contentType: Option[String], extension: String): String = {
    // Generate a name for the temp file
    val tempName = java.util.UUID.randomUUID.toString + "." + extension

    // Upload the original file to the temp location for kraken
    S3Client.uploadTempFile(bucket, tempName, bytes, contentType)
  }

}