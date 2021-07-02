package com.lucidchart.open.cashy.config

import javax.inject.Inject
import play.api.Configuration

class Buckets(bucketCloudfrontMap: Map[String, String]) {
  @Inject()
  def this(configuration: Configuration) =
    this(
      configuration
        .get[Map[String, Configuration]]("amazon.s3.bucketCloudfrontMap")
        .map { case (k, c) => (k -> c.get[String]("cloudfront")) }
    )

  def names = bucketCloudfrontMap.keySet

  def cloudfrontUrl(bucket: String): String = bucketCloudfrontMap(bucket)

  def contains(name: String): Boolean = bucketCloudfrontMap.contains(name)
}
