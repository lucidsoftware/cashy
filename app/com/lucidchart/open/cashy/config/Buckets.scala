package com.lucidchart.open.cashy.config

import javax.inject.Inject
import play.api.Configuration

class Buckets(bucketUrlMap: Map[String, String]) {
  @Inject()
  def this(configuration: Configuration) =
    this(
      configuration
        .get[Map[String, String]]("amazon.s3.bucketUrlMap")
    )

  def names = bucketUrlMap.keySet

  def publicUrl(bucket: String): String = bucketUrlMap(bucket)

  def contains(name: String): Boolean = bucketUrlMap.contains(name)
}
