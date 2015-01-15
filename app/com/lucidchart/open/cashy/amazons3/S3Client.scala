package com.lucidchart.open.cashy.amazons3

import java.io.ByteArrayInputStream
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.amazonaws.auth.BasicAWSCredentials
import scala.collection.JavaConverters._

case class ListObjectsResponse(
  folders: List[String],
  assets: List[String],
  currMarker: Option[String],
  nextMarker: Option[String]
)

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

  def createFolder(bucketName: String, key: String) {
    try {
      s3Client.putObject(bucketName, key, new java.io.ByteArrayInputStream(new Array[Byte](0)), new ObjectMetadata())
    } catch {
      case e: AmazonClientException => {
        Logger.error(s"Error when creating folder (uploading) to S3 $bucketName/$key", e)
        throw e
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

  def listObjects(bucketName: String, prefix: String): ListObjectsResponse = {
    val listRequest = new ListObjectsRequest()
    listRequest.setBucketName(bucketName)
    listRequest.setPrefix(prefix)
    listRequest.setDelimiter("/")

    try {
      val objectListing = s3Client.listObjects(listRequest)
      val folders = objectListing.getCommonPrefixes().asScala.toList
      val assets = objectListing.getObjectSummaries().asScala.toList.map(_.getKey()).filter(_ != prefix)
      val currentMarker = Option(objectListing.getNextMarker())
      ListObjectsResponse(folders, assets, None, currentMarker)

    } catch {
      case e: Exception => {
        Logger.error("Error listing objects")
        throw e
      }
    }

  }

}