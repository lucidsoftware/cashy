package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.utils.{FileHandler, KrakenClient}
import com.lucidchart.open.cashy.config.{ExtensionsConfig, ExtensionType, UploadFeatureConfig}
import com.lucidchart.open.cashy.uploaders._

import java.io.File
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Request, Action}
import play.api.Play.current
import play.api.Play.configuration
import play.api.data._
import play.api.data.Forms._
import validation.Constraints

object UploadController extends UploadController
class UploadController extends AppController with ExtensionsConfig with UploadFeatureConfig {

  val logger = Logger(this.getClass)

  val minNestedDirectories: Int = configuration.getInt("upload.minNestedDirectories").get

  val filenameRegex = """[a-zA-Z0-9-_\./@]+""".r

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

            // Get the file bytes and content type
            val (bytes, contentType) = fileOption.get match {
              case FilePart(key, filename, contentType, bytes) => {
                (bytes, contentType)
              }
            }

            val uploadResult = getExtensionType(data.assetName) match {
              case ExtensionType.js => {
                JsUploader.upload(bytes, contentType, user, data)
              }
              case ExtensionType.css => {
                CssUploader.upload(bytes, contentType, user, data)
              }
              case ExtensionType.image => {
                if (uploadFeatures.kraken) {
                  KrakenImageUploader.upload(bytes, contentType, user, data)
                } else {
                  DefaultUploader.upload(bytes, contentType, user, data)
                }
              }
              case ExtensionType.valid => {
                DefaultUploader.upload(bytes, contentType, user, data)
              }
              case _ => {
                throw new UploadFailedException("Asset type not supported")
              }
            }

            Ok(views.html.upload.complete(uploadResult))

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

}

