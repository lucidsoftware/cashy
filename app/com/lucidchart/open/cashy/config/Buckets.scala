package com.lucidchart.open.cashy.config

import javax.inject.Inject
import play.api.Configuration

case class BucketConfig(cloudfrontId: String, bucketName: Option[String] = None)

class Buckets(bucketConfigs: Map[String, BucketConfig]) {
  @Inject()
  def this(configuration: Configuration) = this(
    configuration.get[Map[String, Configuration]]("amazon.s3.bucketCloudfrontMap").map {
      case (name, c) => name -> BucketConfig(c.get[String]("cloudfront"), c.getOptional[String]("bucketName"))
    }
  )

  def names = bucketConfigs.keySet

  def cloudfrontUrl(bucket: String): String = bucketConfigs(bucket).cloudfrontId

  def contains(name: String): Boolean = bucketConfigs.contains(name)

  def bucketNameForSDK(bucket: String): String = bucketConfigs(bucket).bucketName.getOrElse(bucket)
}
