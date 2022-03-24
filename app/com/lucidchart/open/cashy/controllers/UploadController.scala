package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.{Validation, S3Client}
import com.lucidchart.open.cashy.utils.{FileHandler, KrakenClient, AssetDataHelper}
import com.lucidchart.open.cashy.config.{ExtensionsConfig, ExtensionType, UploadFeatures, Buckets}
import com.lucidchart.open.cashy.uploaders._

import java.io.File
import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.mvc.{Request, Action, AbstractController, ControllerComponents}
import play.api.mvc.MultipartFormData.FilePart
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import scala.util.{Try, Success, Failure}
import validation.Constraints

case class AssetNotFoundException(message: String) extends Exception(message)

class UploadController @Inject() (
    authAction: AuthAction,
    s3Client: S3Client,
    krakenClient: KrakenClient,
    configuration: Configuration,
    jsUploader: JsUploader,
    cssUploader: CssUploader,
    krakenImageUploader: KrakenImageUploader,
    defaultUploader: DefaultUploader,
    components: ControllerComponents
)(implicit
    private val uploadFeatures: UploadFeatures,
    private val buckets: Buckets,
    private val extensions: ExtensionsConfig
) extends AbstractController(components)
    with I18nSupport {

  implicit val executionContext = defaultExecutionContext

  val logger = Logger(this.getClass)

  val minNestedDirectories: Int = configuration.get[Int]("upload.minNestedDirectories")

  private val uploadForm = Form(
    mapping(
      "bucket" -> text.verifying("Invalid bucket", x => buckets.contains(x)),
      "assetName" -> text
        .verifying("Enter a name", x => !x.isEmpty)
        .verifying("Must not start with /", x => !x.startsWith("/"))
        .verifying("Must not contain invalid characters", Validation.isSafeS3Key _)
        .verifying("Must not contain ./", x => !x.contains("./"))
        .verifying(
          "Must be organized in at least " + minNestedDirectories + " directories",
          x => x.split("/").length >= minNestedDirectories + 1
        )
        .verifying(
          "Must end in a valid extension (" + extensions(ExtensionType.valid)
            .map("." + _)
            .mkString(", ") + ")",
          x => extensions.getExtensionType(x) != ExtensionType.invalid
        ),
      "resizeImage" -> boolean,
      "resizedImage" -> optional(text),
      "uploadRetina" -> boolean,
      "assetRetinaName" -> optional(text),
      "imageWidth" -> optional(number),
      "imageHeight" -> optional(number),
      "assetURL" -> optional(text)
    )(UploadFormSubmission.apply _)(UploadFormSubmission.unapply _)
      .verifying(
        "Name not available",
        form =>
          form match {
            case UploadFormSubmission(
                  bucket,
                  assetName,
                  resizeImage,
                  resizedImage,
                  uploadRetina,
                  assetRetinaName,
                  imageWidth,
                  imageHeight,
                  assetURL
                ) =>
              !s3Client.existsInS3(bucket, assetName)
          }
      )
      .verifying(
        "Must specify non-negative dimensions",
        form =>
          form match {
            case UploadFormSubmission(
                  bucket,
                  assetName,
                  resizeImage,
                  resizedImage,
                  uploadRetina,
                  assetRetinaName,
                  imageWidth,
                  imageHeight,
                  assetURL
                ) =>
              !resizeImage || (imageWidth.isDefined && imageWidth.get > 0 && imageHeight.isDefined && imageHeight.get > 0)
          }
      )
      .verifying(
        "Must preview resized image",
        form =>
          form match {
            case UploadFormSubmission(
                  bucket,
                  assetName,
                  resizeImage,
                  resizedImage,
                  uploadRetina,
                  assetRetinaName,
                  imageWidth,
                  imageHeight,
                  assetURL
                ) =>
              !resizeImage || (resizedImage.isDefined)
          }
      )
      .verifying(
        "Must provide retina image name",
        form =>
          form match {
            case UploadFormSubmission(
                  bucket,
                  assetName,
                  resizeImage,
                  resizedImage,
                  uploadRetina,
                  assetRetinaName,
                  imageWidth,
                  imageHeight,
                  assetURL
                ) =>
              !resizeImage || (!uploadRetina || assetRetinaName.isDefined)
          }
      )
  )

  private case class KrakenPreviewFormSubmission(
      bucket: String,
      width: Int,
      height: Int,
      assetURL: Option[String]
  )
  private val krakenForm = Form(
    mapping(
      "bucket" -> text.verifying("Invalid bucket", x => buckets.contains(x)),
      "width" -> number.verifying("Must be positive", x => x > 0),
      "height" -> number.verifying("Must be positive", x => x > 0),
      "assetURL" -> optional(text)
    )(KrakenPreviewFormSubmission.apply)(KrakenPreviewFormSubmission.unapply)
  )

  /**
    * Upload form, authentication required
    */
  def index(bucket: Option[String] = None, path: Option[String] = None, assetURL: Option[String] = None) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        val filledForm =
          uploadForm.fill(
            UploadFormSubmission(bucket.orNull, path.orNull, assetURL = assetURL)
          )

        Ok(views.html.upload.index(filledForm))
      }
    }

  def uploadToS3 =
    authAction.authenticatedUser { implicit user =>
      Action(parse.multipartFormData(FileHandler.handleFilePartAsByteArray)) { implicit request =>
        uploadForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Ok(views.html.upload.index(formWithErrors))
            },
            data => {
              try {
                // Get the asset data
                val assetData =
                  AssetDataHelper.getData(eitherAssetSource(request.body.file("assetFile"), data.assetURL))

                val uploadResult = extensions.getExtensionType(data.assetName) match {
                  case ExtensionType.js => {
                    jsUploader.upload(assetData.bytes, assetData.contentType, user, data)
                  }
                  case ExtensionType.css => {
                    cssUploader.upload(assetData.bytes, assetData.contentType, user, data)
                  }
                  case ExtensionType.image => {
                    if (uploadFeatures.krakenEnabled) {
                      krakenImageUploader.upload(assetData.bytes, assetData.contentType, user, data)
                    } else {
                      defaultUploader.upload(assetData.bytes, assetData.contentType, user, data)
                    }
                  }
                  case ExtensionType.valid => {
                    defaultUploader.upload(assetData.bytes, assetData.contentType, user, data)
                  }
                  case _ => {
                    throw new UploadFailedException("Asset type not supported")
                  }
                }

                Ok(views.html.upload.complete(uploadResult))
              } catch {
                case e: Exception => {
                  logger.error("Exception caught when processing upload request", e)
                  Ok(views.html.upload.index(uploadForm.fill(data), Some(e.getMessage)))
                }
              }
            }
          )
      }
    }

  def krakenPreview =
    authAction.authenticatedUser { implicit user =>
      Action(parse.multipartFormData(FileHandler.handleFilePartAsByteArray)) { implicit request =>
        try {
          krakenForm
            .bindFromRequest()
            .fold(
              formWithErrors => {
                throw new UploadFailedException(
                  formWithErrors.errors.map(e => e.key + ": " + e.message) mkString ("\n")
                )
              },
              data => {

                // Get the asset data
                val assetData =
                  AssetDataHelper.getData(eitherAssetSource(request.body.file("assetFile"), data.assetURL))

                val extension = extensions.getExtension(assetData.filename)

                // Generate a name for the temp file
                val tempName = java.util.UUID.randomUUID.toString + "." + extension

                // Upload the original file to the temp location
                val tempUrl =
                  s3Client.uploadTempFile(data.bucket, tempName, assetData.bytes, assetData.contentType)

                // Send the resize request to Kraken
                val krakenBytes = krakenClient.resizeImage(tempUrl, data.width, data.height)

                // Upload the resized image
                val resizeName = java.util.UUID.randomUUID.toString + "." + extension
                val resizedUrl =
                  s3Client.uploadTempFile(data.bucket, resizeName, krakenBytes, assetData.contentType)

                // Return with url to the resized asset
                val json = Json.toJson(Map("resizedUrl" -> resizedUrl))
                Ok(json)
              }
            )
        } catch {
          case e: Exception => {
            val json = Json.toJson(Map("error" -> e.getMessage))
            Ok(json)
          }
        }
      }
    }

  /**
    * This method is actually an endpoint that will take take the form and check it. Any errors
    * will be sent back to the user, or, if the form was valid, return a success message.
    */
  def validate =
    authAction.authenticatedUser { implicit user =>
      Action(parse.multipartFormData(FileHandler.handleFilePartAsByteArray)) { implicit request =>
        val response = uploadForm
          .bindFromRequest()
          .fold(
            formWithErrors => formWithErrors.errors,
            data => {
              Try {
                AssetDataHelper.getData(eitherAssetSource(request.body.file("assetFile"), data.assetURL))
              } match {
                case Success(_) => List.empty
                case Failure(e) => List(new FormError("", e.getMessage))
              }
            }
          )
          .map { error =>
            (if (error.key != "") error.key else "all") -> error.message
          }

        val json = if (response.size == 0) {
          Json.obj("success" -> true)
        } else {
          Json.toJson(response.groupBy(_._1).view.mapValues(_.map(_._2)))
        }
        Ok(json)
      }
    }

  private def eitherAssetSource(
      fileOption: Option[FilePart[Array[Byte]]],
      urlOption: Option[String]
  ): Either[FilePart[Array[Byte]], String] = {
    fileOption.map(Left(_)) orElse urlOption.map(Right(_)) getOrElse (throw AssetNotFoundException(
      s"Could not parse file and/or could not download from $urlOption"
    ))
  }

}
