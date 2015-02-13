package com.lucidchart.open.cashy.config

import play.api.Play.{configuration, current}
import scala.collection.JavaConverters._

object ExtensionType extends Enumeration {
  val invalid = Value(0, "INVALID")
  val valid  = Value(1, "VALID")
  val js  = Value(2, "JS")
  val css = Value(3, "CSS")
  val image = Value(4, "IMG")
}

trait ExtensionsConfig {
  private val uploadExtensions: Set[String] = configuration.getStringList("upload.extensions").get.asScala.toSet
  private val jsExtensions: Set[String] = configuration.getStringList("upload.jsExtensions").get.asScala.toSet
  private val cssExtensions: Set[String] = configuration.getStringList("upload.cssExtensions").get.asScala.toSet
  private val imageExtensions: Set[String] = configuration.getStringList("upload.imageExtensions").get.asScala.toSet

  implicit val extensions: Map[ExtensionType.Value,Set[String]] = Map(
    ExtensionType.valid -> uploadExtensions,
    ExtensionType.js -> jsExtensions,
    ExtensionType.css -> cssExtensions,
    ExtensionType.image -> imageExtensions
  )

  // Returns true if the extension exists (case insensitive)
  private def checkExtension(extensions: Set[String], key: String): Boolean = {
    extensions.exists(e => key.toLowerCase().endsWith(e.toLowerCase()))
  }

  // Returns the extension type for a key
  protected def getExtensionType(key: String): ExtensionType.Value = {
    if (checkExtension(extensions(ExtensionType.js), key)) {
      ExtensionType.js
    } else if (checkExtension(extensions(ExtensionType.css), key)) {
      ExtensionType.css
    } else if (checkExtension(extensions(ExtensionType.image), key)) {
      ExtensionType.image
    } else if (checkExtension(extensions(ExtensionType.valid), key)) {
      ExtensionType.valid
    } else {
      ExtensionType.invalid
    }
  }

  // Check if the file is minified by looking for .min in the file name
  protected def checkMinified(key: String): Boolean = {
    key.substring(key.lastIndexOf("/")).contains(".min")
  }

  // Returns the actual extension used in the filename
  protected def getExtension(key: String): String = {
    key.substring(key.lastIndexOf(".")+1)
  }
}
