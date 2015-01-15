package com.lucidchart.open.cashy.amazons3

import java.io.ByteArrayInputStream
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.auth.BasicAWSCredentials

object S3Client extends S3Client
class S3Client {
  val logger = Logger(this.getClass)

  protected val uploadTimeout = configuration.getInt("amazon.s3.upload.timeout").get
  protected val uploadCacheTime = configuration.getInt("amazon.s3.upload.cachetime").get
  protected val accessKey = configuration.getString("amazon.s3.credentials.accesskey").get
  protected val secret = configuration.getString("amazon.s3.credentials.secret").get

  protected val awsCredentials = new BasicAWSCredentials(accessKey, secret)
  protected val s3Client = new AmazonS3Client(awsCredentials)


  def existsInS3(bucketName: String, objectName: String): Boolean = {
    try {
      s3Client.getObjectMetadata(bucketName, objectName)
      true
    }
    catch {
      case e: AmazonS3Exception => {
        if (e.getStatusCode() == 404) {
          false
        } else {
          throw e
        }
      }
    }
  }

  def uploadToS3(bucketName: String, assetName: String,  bytes: Array[Byte], contentType: Option[String], gzipped: Boolean = false): Boolean = {
    val metadata = new ObjectMetadata
    metadata.setContentLength(bytes.length)
    metadata.setCacheControl("public; no-transform; max-age=" + uploadCacheTime)
    if (contentType.isDefined)
      metadata.setContentType(contentType.get)

    if (gzipped) {
      metadata.setContentEncoding("gzip")
    }

    val objectName = if(gzipped) assetName + ".gz" else assetName

    try {
      s3Client.putObject(new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(bytes), metadata))
      true
    } catch {
      case e: AmazonClientException => {
        Logger.error("Error while uploading to S3 " + objectName, e)
        throw e
      }
    }
  }

  def removeFromS3(bucketName: String, assetName: String, gzipped: Boolean = false) {
    val objectName = if(gzipped) assetName + ".gz" else assetName
    try {
      s3Client.deleteObject(bucketName, assetName)
    } catch {
      case e: Exception => {
        Logger.error(s"Error when deleting asset $bucketName/$objectName")
        throw e
      }
    }
  }

}