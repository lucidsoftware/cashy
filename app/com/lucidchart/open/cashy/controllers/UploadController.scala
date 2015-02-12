package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.models.{AssetModel, AuditModel, User, Asset}
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.utils.{GzipHelper, FileHandler, JsCompress, CssCompress, KrakenClient, DownloadHelper}
import com.lucidchart.open.cashy.config.{ExtensionsConfig, ExtensionType}

import java.io.File
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Request, Action}
import play.api.Play.current
import play.api.Play.configuration
import play.api.data._
import play.api.data.Forms._
import scala.collection.mutable.MutableList
import validation.Constraints

case class UploadFailedException(message: String) extends Exception(message)

object UploadController extends UploadController
class UploadController extends AppController with ExtensionsConfig {

  val logger = Logger(this.getClass)

  val minNestedDirectories: Int = configuration.getInt("upload.minNestedDirectories").get
  val minGzipSavings: Double = configuration.getDouble("upload.minGzipSavings").get

  implicit val krakenEnabled = configuration.getBoolean("kraken.enabled").getOrElse(false)

  val filenameRegex = """[a-zA-Z0-9-_\./@]+""".r

  private case class UploadFormSubmission(
    bucket: String,
    assetName: String,
    resizeImage: Boolean = false,
    resizedImage: Option[String] = None,
    uploadRetina: Boolean = false,
    assetRetinaName: Option[String] = None,
    imageWidth: Option[Int] = None,
    imageHeight: Option[Int] = None
  )
  private val uploadForm = Form(
    mapping(
      "bucket" -> text.verifying("Invalid bucket", x => buckets.contains(x)),
      "assetName" -> text.verifying("Enter a name", x => !x.isEmpty)
              .verifying("Must not start with /", x => !x.startsWith("/"))
              .verifying("Must not contain invalid characters", x => filenameRegex.unapplySeq(x).isDefined)
              .verifying("Must not contain ./", x => !x.contains("./"))
              .verifying("Must be organized in at least " + minNestedDirectories + " directories", x => x.split("/").length >= minNestedDirectories + 1)
              .verifying("Must end in a valid extension (" + extensions(ExtensionType.valid).map("." + _).mkString(", ") + ")", x => getExtensionType(x) != ExtensionType.invalid),
      "resizeImage" -> boolean,
      "resizedImage" -> optional(text),
      "uploadRetina" -> boolean,
      "assetRetinaName" -> optional(text),
      "imageWidth" -> optional(number),
      "imageHeight" -> optional(number)
    )(UploadFormSubmission.apply)(UploadFormSubmission.unapply)
      .verifying("Name not available", form => form match {
        case UploadFormSubmission(bucket, assetName, resizeImage, resizedImage, uploadRetina, assetRetinaName, imageWidth, imageHeight) => !S3Client.existsInS3(bucket, assetName)
      })
      .verifying("Must specify non-negative dimensions", form => form match {
        case UploadFormSubmission(bucket, assetName, resizeImage, resizedImage, uploadRetina, assetRetinaName, imageWidth, imageHeight) => !resizeImage || (imageWidth.isDefined && imageWidth.get > 0 && imageHeight.isDefined && imageHeight.get > 0)
      })
      .verifying("Must preview resized image", form => form match {
        case UploadFormSubmission(bucket, assetName, resizeImage, resizedImage, uploadRetina, assetRetinaName, imageWidth, imageHeight) => !resizeImage || (resizedImage.isDefined)
      })
      .verifying("Must provide retina image name", form => form match {
        case UploadFormSubmission(bucket, assetName, resizeImage, resizedImage, uploadRetina, assetRetinaName, imageWidth, imageHeight) => !resizeImage || (!uploadRetina || assetRetinaName.isDefined)
      })
  )

  private case class KrakenPreviewFormSubmission(
    bucket: String,
    width: Int,
    height: Int
  )
  private val krakenForm = Form(
    mapping(
      "bucket" -> text.verifying("Invalid bucket", x => buckets.contains(x)),
      "width" -> number.verifying("Must be positive", x => x > 0),
      "height" -> number.verifying("Must be positive", x => x > 0)
    )(KrakenPreviewFormSubmission.apply)(KrakenPreviewFormSubmission.unapply)
  )

  /**
   * Upload form, authentication required
   */
  def index(bucket: Option[String] = None, path: Option[String] = None) = AuthAction.authenticatedUser { implicit user =>
    Action {  implicit request =>

      val filledForm = uploadForm.fill(UploadFormSubmission(bucket.getOrElse(null), path.getOrElse(null)))

      Ok(views.html.upload.index(filledForm))
    }
  }

  def uploadToS3 = AuthAction.authenticatedUser { implicit user =>
    Action(parse.multipartFormData(FileHandler.handleFilePartAsByteArray)) { implicit request =>

      val fileOption = request.body.file("assetFile")
      val formWithData = uploadForm.bindFromRequest()
      val formWithFileValidation = fileOption match {
        case None => {
          formWithData.copy(errors = formWithData.errors :+ new FormError("assetFile", "Must select a file"))
        }
        case Some(file) => formWithData
      }

      formWithFileValidation.fold(
        formWithErrors => {
          Ok(views.html.upload.index(formWithErrors))
        },
        data => {

          try {

            val bucket = data.bucket
            val assetName = data.assetName

            // Get the file bytes and content type
            val (bytes, contentType) = fileOption.get match {
              case FilePart(key, filename, contentType, bytes) => {
                (bytes, contentType)
              }
            }

            val assets = MutableList[Tuple2[String,Asset]]()
            val existingAssets = MutableList[Tuple2[String,Asset]]()

            val unmodifiedAsset = getExtensionType(assetName) match {
              case ExtensionType.js => {

                // Upload the asset
                val asset = upload(bytes, bucket, assetName, contentType, user)

                if(checkMinified(assetName)) {
                  assets += (("Minified", asset))
                } else {
                  assets += (("Original", asset))

                  val extension = getExtension(assetName)
                  val minAssetName = assetName.substring(0, assetName.toLowerCase.lastIndexOf("." + extension.toLowerCase)) + ".min." + extension

                  // Make sure a min version with this name does not already exist
                  if(!S3Client.existsInS3(bucket, minAssetName)) {
                    val (minBytes, compressErrors) = JsCompress.compress(bytes)

                    if (compressErrors.size > 0) {
                      throw new UploadFailedException("Minifying javascript failed: " + (bucket, assetName) + "\n" + compressErrors.mkString("\n"))
                    }

                    val minAsset = upload(minBytes, bucket, minAssetName, contentType, user)
                    assets += (("Minified", minAsset))
                  } else {
                    // A min version already exists, find it in cashy and return as an existing asset
                    val minAsset = AssetModel.findByKey(bucket, minAssetName).get
                    existingAssets += (("Minified", minAsset))
                  }
                }

                asset
              }
              case ExtensionType.css => {
                // Upload the asset
                val asset = upload(bytes, bucket, assetName, contentType, user)

                if(checkMinified(assetName)) {
                  assets += (("Minified", asset))
                } else {
                  assets += (("Original", asset))

                  // If it is css and not already minified, try to minify it
                  val extension = getExtension(assetName)
                  val minAssetName = assetName.substring(0, assetName.toLowerCase.lastIndexOf("." + extension.toLowerCase)) + ".min." + extension

                  // Make sure a min version with this name does not already exist
                  if(!S3Client.existsInS3(bucket, minAssetName)) {
                    val minBytes = CssCompress.compress(bytes)

                    val minAsset = upload(minBytes, bucket, minAssetName, contentType, user)
                    assets += (("Minified", minAsset))
                  } else {
                    val minAsset = AssetModel.findByKey(bucket, minAssetName).get
                    existingAssets += (("Minified", minAsset))
                  }
                }

                asset
              }
              case ExtensionType.image => {
                if (krakenEnabled) {
                  val extension = getExtension(assetName)
                  // If the image is resized
                  if(data.resizeImage) {

                    val resizeWidth = data.imageWidth.get
                    val resizeHeight = data.imageHeight.get

                    // Get the bytes from the already resized image (when the user previewed it)
                    val resizedBytes = DownloadHelper.downloadBytes(data.resizedImage.get)

                    // Upload it to S3
                    val asset = upload(resizedBytes, bucket, assetName, contentType, user)
                    assets += ((resizeWidth + "x" + resizeHeight, asset))

                    // If retina option is checked
                    if(data.uploadRetina) {
                      val retinaName = data.assetRetinaName.get

                      // Check if @2x name is taken
                      if(!S3Client.existsInS3(bucket, retinaName)) {
                        // Generate a name for the temp file
                        val tempName = java.util.UUID.randomUUID.toString + "." + extension

                        // Upload the original file to the temp location
                        val tempUrl = S3Client.uploadTempFile(bucket, tempName, bytes, contentType)

                        // Send the resize request to Kraken
                        val retinaWidth = resizeWidth * 2
                        val retinaHeight = resizeHeight * 2
                        val retinaBytes = KrakenClient.resizeImage(tempUrl, retinaWidth, retinaHeight)

                        // Upload to S3
                        val retinaAsset = upload(retinaBytes, bucket, retinaName, contentType, user)
                        assets += (("Retina", retinaAsset))

                      } else {
                        // Get that asset and return it
                        val retinaAsset = AssetModel.findByKey(bucket, retinaName).get
                        existingAssets += (("Retina", retinaAsset))
                      }
                    }
                    asset
                  } else {
                    // Generate a name for the temp file
                    val tempName = java.util.UUID.randomUUID.toString + "." + extension

                    // Upload the original file to the temp location for kraken
                    val tempUrl = S3Client.uploadTempFile(bucket, tempName, bytes, contentType)
                    val compressedBytes = KrakenClient.compressImage(tempUrl)

                    // upload to s3
                    val asset = upload(compressedBytes, bucket, assetName, contentType, user)
                    assets += (("Original", asset))
                    asset
                  }
                } else {
                  // upload to s3
                  val asset = upload(bytes, bucket, assetName, contentType, user)
                  assets += (("Original", asset))
                  asset
                }
              }
              case ExtensionType.valid => {
                val asset = upload(bytes, bucket, assetName, contentType, user)
                assets += (("Original", asset))
                asset
              }
              case _ => {
                throw new UploadFailedException("Asset type not supported")
              }
            }

            Ok(views.html.upload.complete(assets.toList, existingAssets.toList, unmodifiedAsset.bucket, unmodifiedAsset.parent))

          }
          catch {
            case e: Exception => {
              logger.error("Exception caught when processing upload request", e)
              Ok(views.html.upload.index(uploadForm.fill(data), Some(e.getMessage)))
            }
          }
        }
      )
    }
  }

  def krakenPreview = AuthAction.authenticatedUser { implicit user =>
    Action(parse.multipartFormData(FileHandler.handleFilePartAsByteArray)) { implicit request =>

      val fileOption = request.body.file("assetFile")
      val formWithData = krakenForm.bindFromRequest()
      try {
        fileOption match {
          case None => {
            throw new Exception("File not found")
          }
          case Some(file) => {
            formWithData.fold(
              formWithErrors => {
                throw new Exception(formWithErrors.errors.map(e => e.key + ": " + e.message)mkString("\n"))
              },
              data => {
                // Get the file bytes and content type
                val (bytes, contentType, filename) = file match {
                  case FilePart(key, filename, contentType, bytes) => {
                    (bytes, contentType, filename)
                  }
                }

                val bucket = data.bucket
                val width = data.width
                val height = data.height

                val extension = getExtension(filename)

                // Generate a name for the temp file
                val tempName = java.util.UUID.randomUUID.toString + "." + extension

                // Upload the original file to the temp location
                val tempUrl = S3Client.uploadTempFile(bucket, tempName, bytes, contentType)

                // Send the resize request to Kraken
                val krakenBytes = KrakenClient.resizeImage(tempUrl, width, height)

                // Upload the resized image
                val resizeName = java.util.UUID.randomUUID.toString + "." + extension
                val resizedUrl = S3Client.uploadTempFile(bucket, resizeName, krakenBytes, contentType)

                // Return with url to the resized asset
                val json = Json.stringify(Json.toJson(Map("resizedUrl" -> resizedUrl)))
                Ok(json).withHeaders("Content-Type" -> "application/json")
              }
            )
          }
        }
      }
      catch {
        case e: Exception => {
          val json = Json.stringify(Json.toJson(Map("error" -> e.getMessage)))
          Ok(json).withHeaders("Content-Type" -> "application/json")
        }
      }
    }
  }


  // Check if gzip compression saves enough to meet the threshold and uploads to s3 if so
  private def gzipAndUpload(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String]): Boolean = {
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
  private def upload(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String], user: User): Asset = {
    val gzipUploaded = gzipAndUpload(bytes, bucket, assetName, contentType)

    // Try to upload the asset to S3
    if(S3Client.uploadToS3(bucket, assetName, bytes, contentType)) {
      val assetId = AssetModel.createAsset(bucket, assetName, user.id)
      val asset = AssetModel.findById(assetId).get

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

}

