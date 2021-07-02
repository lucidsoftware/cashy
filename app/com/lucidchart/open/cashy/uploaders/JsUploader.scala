package com.lucidchart.open.cashy.uploaders

import javax.inject.Inject
import com.lucidchart.open.cashy.models.{Asset, User, AssetModel}
import com.lucidchart.open.cashy.utils.JsCompress

class JsUploader @Inject() (assetModel: AssetModel, components: UploaderComponents)
    extends Uploader(components) {

  override def upload(
      bytes: Array[Byte],
      contentType: Option[String],
      user: User,
      data: UploadFormSubmission
  ): UploadResult = {
    val bucket = data.bucket
    val assetName = data.assetName
    val uploadedAssets = List.newBuilder[(String, Asset)]
    val existingAssets = List.newBuilder[(String, Asset)]

    // Upload the asset
    val asset = uploadAndAudit(bytes, bucket, assetName, contentType, user)

    if (extensionsConfig.checkMinified(assetName)) {
      uploadedAssets += (("Minified", asset))
    } else {
      uploadedAssets += (("Original", asset))

      if (uploadFeatures.compressJsEnabled) {
        val extension = extensionsConfig.getExtension(assetName)
        val minAssetName =
          assetName.substring(
            0,
            assetName.toLowerCase.lastIndexOf("." + extension.toLowerCase)
          ) + ".min." + extension

        // Make sure a min version with this name does not already exist
        if (!s3Client.existsInS3(bucket, minAssetName)) {
          val (minBytes, compressErrors) = JsCompress.compress(bytes)

          if (compressErrors.size > 0) {
            throw new UploadFailedException(
              "Minifying javascript failed: " + (bucket, assetName) + "\n" + compressErrors.mkString("\n")
            )
          }

          val minAsset = uploadAndAudit(minBytes, bucket, minAssetName, contentType, user)
          uploadedAssets += (("Minified", minAsset))
        } else {
          // A min version already exists, find it in cashy and return as an existing asset
          val minAsset = assetModel.findByKey(bucket, minAssetName).get
          existingAssets += (("Minified", minAsset))
        }
      }
    }

    UploadResult(
      uploadedAssets.result(),
      existingAssets.result(),
      asset.bucket,
      asset.parent
    )
  }

}
