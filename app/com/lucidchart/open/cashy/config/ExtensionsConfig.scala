package com.lucidchart.open.cashy.config

import play.api.Play.{configuration, current}
import scala.collection.JavaConverters._

case class Extensions(
  all: Set[String],
  js: Set[String]
)

trait ExtensionsConfig {
  private val uploadExtensions: Set[String] = configuration.getStringList("upload.extensions").get.asScala.toSet
  private val jsExtensions: Set[String] = configuration.getStringList("upload.jsExtensions").get.asScala.toSet

  implicit val extensions = Extensions(uploadExtensions, jsExtensions)

  // Returns true if the extension exists (case insensitive)
  protected def checkExtension(extensions: Set[String], key: String): Boolean = {
    extensions.exists(e => key.toLowerCase().endsWith(e.toLowerCase()))
  }

  // Check if the file is minified by looking for .min in the file name
  protected def checkMinified(key: String): Boolean = {
    key.substring(key.lastIndexOf("/")).contains(".min")
  }

  protected def getExtension(key: String): String = {
    key.substring(key.lastIndexOf(".")+1)
  }
}
