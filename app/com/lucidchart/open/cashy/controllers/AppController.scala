package com.lucidchart.open.cashy.controllers

import play.api.Play.current
import play.api.Play.configuration
import play.api.mvc.Controller

trait AppController extends Controller {
  protected val bucketCloudfrontMap = configuration.getConfig("amazon.s3.bucketCloudfrontMap").get.subKeys.map(k => (k -> configuration.getString(s"amazon.s3.bucketCloudfrontMap.$k.cloudfront").get)).toMap
  implicit val buckets: Set[String] = bucketCloudfrontMap.keys.toSet
}
