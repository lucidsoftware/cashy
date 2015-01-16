package com.lucidchart.open.cashy.config

import play.api.Play.{configuration, current}

trait CloudfrontConfig {
  protected val bucketCloudfrontMap = configuration.getConfig("amazon.s3.bucketCloudfrontMap").get.subKeys.map(k => (k -> configuration.getString(s"amazon.s3.bucketCloudfrontMap.$k.cloudfront").get)).toMap
}
