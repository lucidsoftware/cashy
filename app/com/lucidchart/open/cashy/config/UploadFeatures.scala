package com.lucidchart.open.cashy.config

import javax.inject.Inject
import play.api.Configuration

case class UploadFeatures(
  compressJsEnabled: Boolean,
  compressCssEnabled: Boolean,
) {
  @Inject()
  def this(configuration: Configuration) =
    this(
      configuration.getOptional[Boolean]("upload.jsCompression.enabled").getOrElse(false),
      configuration.getOptional[Boolean]("upload.cssCompression.enabled").getOrElse(false),
    )
}
