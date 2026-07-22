package com.lucidchart.open.cashy.amazons3

import java.io.ByteArrayInputStream
import java.nio.file.Paths
import javax.inject.Inject
import play.api.Configuration
import play.api.Logger
import scala.jdk.CollectionConverters._
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider,
  DefaultCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.{S3Client => AmazonS3Client}
import software.amazon.awssdk.core.sync.RequestBody
import com.lucidchart.open.cashy.config.Buckets

case class ListObjectsResponse(
    folders: List[String],
    assets: List[String],
    nextMarker: Option[String]
)

case class AssetMetadata(
    eTag: Option[String],
    contentLength: Option[Long],
    cacheControl: Option[String],
    contentType: Option[String]
)

class S3Client @Inject() (
  buckets: Buckets,
  configuration: Configuration
) {
  val logger = Logger(this.getClass)

  protected val uploadTimeout = configuration.get[Int]("amazon.s3.upload.timeout")
  protected val uploadCacheTime = configuration.get[Int]("amazon.s3.upload.cachetime")
  protected val listingMaxKeys = configuration.get[Int]("amazon.s3.listing.maxKeys")
  protected val tempUploadPrefix = configuration.get[String]("amazon.s3.tempUploadPrefix")
  private val credentialsFile = configuration.getOptional[String]("aws.credentialsFile")

  protected val awsCredentialsProvider = getAwsCredentialsProvider()
  protected val s3Client = AmazonS3Client.builder().credentialsProvider(awsCredentialsProvider).build()

  def existsInS3(bucketName: String, objectName: String): Boolean = {
    headObject(bucketName, objectName).isDefined
  }

  private def headObject(bucketName: String, key: String): Option[HeadObjectResponse] = {
    try {
      Some(s3Client.headObject(
        HeadObjectRequest.builder()
          .bucket(buckets.bucketNameForSDK(bucketName))
          .key(key)
          .build()
      ))
    } catch {
      case _: NoSuchKeyException => None
    }
  }

  def getAssetMetadata(bucketName: String, key: String): Option[AssetMetadata] = {
    headObject(bucketName, key).map { h =>
      AssetMetadata(
        Option(h.eTag()).map(_.replaceAll("^\"|\"$", "")),
        Option(h.contentLength()).map(_.longValue),
        Option(h.cacheControl()),
        Option(h.contentType())
      )
    }
  }

  def createFolder(bucketName: String, key: String): Unit = {
    try {
      s3Client.putObject(
        PutObjectRequest
          .builder()
          .bucket(buckets.bucketNameForSDK(bucketName))
          .key(key)
          .build(),
        RequestBody.fromBytes(Array(0))
      )
    } catch {
      case e: SdkException => {
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
    val objectName = if (gzipped) assetName + ".gz" else assetName

    val requestBuilder = PutObjectRequest
      .builder()
      .bucket(buckets.bucketNameForSDK(bucketName))
      .key(objectName)
      .contentLength(bytes.length)
      .cacheControl(s"public, no-transform, max-age=${uploadCacheTime}")
    contentType.foreach { contentType => requestBuilder.contentType(contentType) }
    if (gzipped) {
      requestBuilder.contentEncoding("gzip")
    }

    try {
      s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(bytes))
      true
    } catch {
      case e: SdkException => {
        logger.error("Error while uploading to S3 " + objectName, e)
        throw e
      }
    }
  }

  def removeFromS3(bucketName: String, assetName: String, gzipped: Boolean = false): Unit = {
    val objectName = if (gzipped) assetName + ".gz" else assetName
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(buckets.bucketNameForSDK(bucketName)).key(assetName).build())
    } catch {
      case e: Exception => {
        logger.error(s"Error when deleting asset $bucketName/$objectName")
        throw e
      }
    }
  }

  def listObjects(bucketName: String, prefix: String, marker: Option[String] = None): ListObjectsResponse = {
    val requestBuilder = ListObjectsV2Request
      .builder()
      .bucket(buckets.bucketNameForSDK(bucketName))
      .prefix(prefix)
      .delimiter("/")
      .maxKeys(listingMaxKeys)

    marker.foreach(requestBuilder.continuationToken(_))

    try {
      val objectListing = s3Client.listObjectsV2(requestBuilder.build())
      val folders =
        objectListing.commonPrefixes.asScala.iterator
          .map(_.prefix)
          .filter(folder => !folder.startsWith(tempUploadPrefix))
          .toList
      val assets =
        objectListing.contents.asScala.iterator.map(_.key).filter(key => key != prefix).toList
      val nextMarker = Option(objectListing.nextContinuationToken())
      ListObjectsResponse(folders, assets, nextMarker)

    } catch {
      case e: Exception => {
        logger.error("Error listing objects")
        throw e
      }
    }
  }

  def listAllObjects(bucketName: String): Iterator[S3SyncAsset] = {
    val listRequest = ListObjectsV2Request.builder().bucket(buckets.bucketNameForSDK(bucketName)).build()

    try {
      val objectListings = s3Client.listObjectsV2Paginator(listRequest)
      for {
        listing <- objectListings.asScala.iterator
        o <- listing.contents.asScala.iterator if !o.key.endsWith("/")
      } yield S3SyncAsset(o.key, o.lastModified)
    } catch {
      case e: Exception => {
        logger.error("Error listing objects")
        throw e
      }
    }
  }

  private def getAwsCredentialsProvider(): AwsCredentialsProvider = {
    credentialsFile
      .map { f =>
        val path = ProfileFile.builder().content(Paths.get(f)).`type`(ProfileFile.Type.CREDENTIALS).build()
        ProfileCredentialsProvider.builder().profileFile(path).build()
      }
      .getOrElse(DefaultCredentialsProvider.create())
  }

}
