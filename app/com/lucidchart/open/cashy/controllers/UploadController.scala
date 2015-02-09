package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.views
import com.lucidchart.open.cashy.models.{AssetModel, AuditModel, User, Asset}
import com.lucidchart.open.cashy.request.{AppFlash, AuthAction}
import com.lucidchart.open.cashy.amazons3.S3Client
import com.lucidchart.open.cashy.utils.{GzipHelper, FileHandler, JsCompress}
import com.lucidchart.open.cashy.config.ExtensionsConfig

import java.io.File
import play.api.Logger
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Request, Action}
import play.api.Play.current
import play.api.Play.configuration
import play.api.data._
import play.api.data.Forms._
import scala.collection.mutable
import validation.Constraints

case class UploadFailedException(message: String) extends Exception(message)

object UploadController extends UploadController
class UploadController extends AppController with ExtensionsConfig {

  val logger = Logger(this.getClass)

  val minNestedDirectories: Int = configuration.getInt("upload.minNestedDirectories").get
  val minGzipSavings: Double = configuration.getDouble("upload.minGzipSavings").get

  val filenameRegex = """[a-zA-Z0-9-_\./@]+""".r

  private case class UploadFormSubmission(
    bucket: String,
    assetName: String
  )
  private val uploadForm = Form(
    mapping(
      "bucket" -> text.verifying("Invalid bucket", x => buckets.contains(x)),
      "assetName" -> text.verifying("Enter a name", x => !x.isEmpty)
              .verifying("Must not start with /", x => !x.startsWith("/"))
              .verifying("Must not contain invalid characters", x => filenameRegex.unapplySeq(x).isDefined)
              .verifying("Must not contain ./", x => !x.contains("./"))
              .verifying("Must be organized in at least " + minNestedDirectories + " directories", x => x.split("/").length >= minNestedDirectories + 1)
              .verifying("Must end in a valid extension (" + extensions.all.map("." + _).mkString(", ") + ")", x => checkExtension(extensions.all, x))
    )(UploadFormSubmission.apply)(UploadFormSubmission.unapply) verifying("Name not available", form => form match {
      case UploadFormSubmission(bucket, assetName) => !S3Client.existsInS3(bucket, assetName)
    })
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

            val assets = mutable.Map[String,Asset]()

            // If it is javascript and not already minified, try to minify it
            if (checkExtension(extensions.js, assetName) && !checkMinified(assetName)) {
              val extension = getExtension(assetName)
              val minAssetName = assetName.substring(0, assetName.toLowerCase.lastIndexOf("." + extension.toLowerCase)) + ".min." + extension

              // Make sure a min version with this name does not already exist
              if(!S3Client.existsInS3(bucket, minAssetName)) {
                val (minBytes, compressErrors) = JsCompress.compress(bytes)

                if (compressErrors.size > 0) {
                  throw new UploadFailedException("Minifying javascript failed: " + (bucket, assetName) + "\n" + compressErrors.mkString("\n"))
                }

                val minAsset = upload(minBytes, bucket, minAssetName, contentType, user)
                assets += ("Minified" -> minAsset)
              }
            }

            val asset = upload(bytes, bucket, assetName, contentType, user)
            assets += ("Original" -> asset)

            Ok(views.html.upload.complete(assets.toMap, asset.bucket, asset.parent))

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

  private def maybeGzip(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String]): Boolean = {
    // Check if gzip compression is appropriate
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

  private def upload(bytes: Array[Byte], bucket: String, assetName: String, contentType: Option[String], user: User): Asset = {
    val gzipUploaded = maybeGzip(bytes, bucket, assetName, contentType)

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

