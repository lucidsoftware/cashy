package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.config.CloudfrontConfig
import play.api.mvc.Controller

trait AppController extends Controller with CloudfrontConfig {
  implicit val buckets: Set[String] = bucketCloudfrontMap.keys.toSet
}
