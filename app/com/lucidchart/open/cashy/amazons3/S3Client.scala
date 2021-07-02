package com.lucidchart.open.cashy.amazons3

import java.io.ByteArrayInputStream
import javax.inject.Inject
import play.api.Logger
import play.api.Configuration
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.AWSCredentialsProvider
import scala.jdk.CollectionConverters._

case class ListObjectsResponse(
    folders: List[String],
    assets: List[String],
    nextMarker: Option[String]
)

class S3Client @Inject() (configuration: Configuration) extends AWSConfig {
  val logger = Logger(this.getClass)

  protected val uploadTimeout = configuration.get[Int]("amazon.s3.upload.timeout")
  protected val uploadCacheTime = configuration.get[Int]("amazon.s3.upload.cachetime")
  protected val listingMaxKeys = configuration.get[Int]("amazon.s3.listing.maxKeys")
  protected val tempUploadPrefix = configuration.get[String]("amazon.s3.tempUploadPrefix")
  protected val s3AccessUrl = configuration.get[String]("amazon.s3.fullAccessUrl")

  protected val awsCredentialsProvider = getAWSCredentialsProvider()
  protected val s3Client = AmazonS3ClientBuilder.standard().withCredentials(awsCredentialsProvider).build()

  def existsInS3(bucketName: String, objectName: String): Boolean = {
    try {
      s3Client.getObjectMetadata(bucketName, objectName)
      true
    } catch {
      case e: AmazonS3Exception => {
        if (e.getStatusCode() == 404) {
          false
        } else {
          throw e
        }
      }
    }
  }

  def createFolder(bucketName: String, key: String): Unit = {
    try {
      s3Client.putObject(
        bucketName,
        key,
        new java.io.ByteArrayInputStream(new Array[Byte](0)),
        new ObjectMetadata()
      )
    } catch {
      case e: AmazonClientException => {
        logger.error(s"Error when creating folder (uploading) to S3 $bucketName/$key", e)
        throw e
      }
    }
  }

  def uploadToS3(
      bucketName: String,
      assetName: String,
      bytes: Array[Byte],
      contentType: Option[String],
      gzipped: Boolean = false
  ): Boolean = {
    val metadata = new ObjectMetadata
    metadata.setContentLength(bytes.length)
    metadata.setCacheControl("public, no-transform, max-age=" + uploadCacheTime)
    contentType.map { contentType =>
      metadata.setContentType(contentType)
    }

    if (gzipped) {
      metadata.setContentEncoding("gzip")
    }

    val objectName = if (gzipped) assetName + ".gz" else assetName

    try {
      s3Client.putObject(
        new PutObjectRequest(bucketName, objectName, new ByteArrayInputStream(bytes), metadata)
      )
      true
    } catch {
      case e: AmazonClientException => {
        logger.error("Error while uploading to S3 " + objectName, e)
        throw e
      }
    }
  }

  // Uploads a file to the temp directory in S3 and returns the full amazon s3 url for it
  def uploadTempFile(
      bucketName: String,
      assetName: String,
      bytes: Array[Byte],
      contentType: Option[String]
  ): String = {
    val tempName = tempUploadPrefix + "/" + assetName
    uploadToS3(bucketName, tempName, bytes, contentType, false)
    s3AccessUrl + bucketName + "/" + tempName
  }

  def removeFromS3(bucketName: String, assetName: String, gzipped: Boolean = false): Unit = {
    val objectName = if (gzipped) assetName + ".gz" else assetName
    try {
      s3Client.deleteObject(bucketName, assetName)
    } catch {
      case e: Exception => {
        logger.error(s"Error when deleting asset $bucketName/$objectName")
        throw e
      }
    }
  }

  def listObjects(bucketName: String, prefix: String, marker: Option[String] = None): ListObjectsResponse = {
    val listRequest = new ListObjectsRequest()
    listRequest.setBucketName(bucketName)
    listRequest.setPrefix(prefix)
    listRequest.setDelimiter("/")
    listRequest.setMaxKeys(listingMaxKeys)

    if (marker.isDefined) {
      listRequest.setMarker(marker.get)
    }

    try {
      val objectListing = s3Client.listObjects(listRequest)
      val folders =
        objectListing
          .getCommonPrefixes()
          .asScala
          .toList
          .filter(folder => !folder.startsWith(tempUploadPrefix))
      val assets =
        objectListing.getObjectSummaries().asScala.toList.map(_.getKey()).filter(key => key != prefix)
      val nextMarker = Option(objectListing.getNextMarker())
      ListObjectsResponse(folders, assets, nextMarker)

    } catch {
      case e: Exception => {
        logger.error("Error listing objects")
        throw e
      }
    }
  }

  def listAllObjects(bucketName: String): List[S3SyncAsset] = {
    val listRequest = new ListObjectsRequest()
    listRequest.setBucketName(bucketName)

    try {
      val objectListing = s3Client.listObjects(listRequest)
      val firstAssets =
        objectListing
          .getObjectSummaries()
          .asScala
          .toList
          .map(o => S3SyncAsset(o.getKey(), o.getLastModified()))
      val remainingAssets = listAllObjectsPaged(objectListing)

      // Filter out anyting ending with a / since those aren't really assets
      (firstAssets ++ remainingAssets).filter(!_.key.endsWith("/"))
    } catch {
      case e: Exception => {
        logger.error("Error listing objects")
        throw e
      }
    }
  }

  private def listAllObjectsPaged(previousListing: ObjectListing): List[S3SyncAsset] = {
    if (!previousListing.isTruncated()) {
      List()
    } else {
      val objectListing = s3Client.listNextBatchOfObjects(previousListing)
      val assets =
        objectListing
          .getObjectSummaries()
          .asScala
          .toList
          .map(o => S3SyncAsset(o.getKey(), o.getLastModified()))
      assets ++ listAllObjectsPaged(objectListing)
    }
  }

}

trait AWSConfig {
  protected def getAWSCredentialsProvider(): AWSCredentialsProvider = {
    try {
      InstanceProfileCredentialsProvider.getInstance()
    } catch {
      case e: Exception =>
        new ProfileCredentialsProvider("/etc/aws/dev-credentials", "default")
    }
  }
}
