package com.lucidchart.open.cashy.config

import play.api.Play.{configuration, current}

case class UploadFeatures(
	kraken: Boolean,
	compressJs: Boolean,
	compressCss: Boolean
)

trait UploadFeatureConfig {
	private val krakenEnabled = configuration.getBoolean("kraken.enabled").getOrElse(false)
	private val jsCompressionEnabled = configuration.getBoolean("upload.jsCompression.enabled").getOrElse(false)
	private val cssCompressionEnabled = configuration.getBoolean("upload.cssCompression.enabled").getOrElse(false)

	implicit val uploadFeatures = UploadFeatures(krakenEnabled, jsCompressionEnabled, cssCompressionEnabled)
}