package com.lucidchart.open.cashy.config

import javax.inject.Inject
import play.api.Configuration
import scala.collection.JavaConverters._

object ExtensionType extends Enumeration {
  val invalid = Value(0, "INVALID")
  val valid = Value(1, "VALID")
  val js = Value(2, "JS")
  val css = Value(3, "CSS")
  val image = Value(4, "IMG")
}

class ExtensionsConfig @Inject() (configuration: Configuration) {

  private[this] def getExtensions(configKey: String): Set[String] =
    configuration.get[Seq[String]](configKey).toSet

  val extensions: Map[ExtensionType.Value, Set[String]] = Map(
    ExtensionType.valid -> getExtensions("upload.extensions"),
    ExtensionType.js -> getExtensions("upload.jsExtensions"),
    ExtensionType.css -> getExtensions("upload.cssExtensions"),
    ExtensionType.image -> getExtensions("upload.imageExtensions")
  )

  def apply(extType: ExtensionType.Value): Set[String] = extensions(extType)

  // Returns true if the extension exists (case insensitive)
  private def checkExtension(extensions: Set[String], key: String): Boolean = {
    extensions.exists(e => key.toLowerCase().endsWith(e.toLowerCase()))
  }

  // Returns the extension type for a key
  def getExtensionType(key: String): ExtensionType.Value = {
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
  def checkMinified(key: String): Boolean = {
    key.substring(key.lastIndexOf("/")).contains(".min")
  }

  // Returns the actual extension used in the filename
  def getExtension(key: String): String = {
    key.substring(key.lastIndexOf(".") + 1)
  }
}
