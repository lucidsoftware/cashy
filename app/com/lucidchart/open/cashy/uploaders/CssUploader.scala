package com.lucidchart.open.cashy.uploaders

import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.models.{Asset, User, AssetModel}
import com.lucidchart.open.cashy.utils.CssCompress

import scala.collection.mutable.MutableList

object CssUploader extends CssUploader
class CssUploader extends Uploader {

  override def upload(bytes: Array[Byte], contentType: Option[String], user: User, data: UploadFormSubmission): UploadResult = {
    val bucket = data.bucket
    val assetName = data.assetName
    val uploadedAssets = MutableList[Tuple2[String,Asset]]()
    val existingAssets = MutableList[Tuple2[String,Asset]]()

    // Upload the asset
    val asset = uploadAndAudit(bytes, bucket, assetName, contentType, user)

    if(checkMinified(assetName)) {
      uploadedAssets += (("Minified", asset))
    } else {
      uploadedAssets += (("Original", asset))

      // If it is css and not already minified, try to minify it
      val extension = getExtension(assetName)
      val minAssetName = assetName.substring(0, assetName.toLowerCase.lastIndexOf("." + extension.toLowerCase)) + ".min." + extension

      // Make sure a min version with this name does not already exist
      if(!S3Client.existsInS3(bucket, minAssetName)) {
        val minBytes = CssCompress.compress(bytes)

        val minAsset = uploadAndAudit(minBytes, bucket, minAssetName, contentType, user)
        uploadedAssets += (("Minified", minAsset))
      } else {
        val minAsset = AssetModel.findByKey(bucket, minAssetName).get
        existingAssets += (("Minified", minAsset))
      }
    }

    UploadResult(
      uploadedAssets.toList,
      existingAssets.toList,
      asset.bucket,
      asset.parent
    )
  }

}