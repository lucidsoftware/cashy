package com.lucidchart.open.cashy.uploaders

import com.lucidchart.open.cashy.models.{Asset, User}

object DefaultUploader extends DefaultUploader
class DefaultUploader extends Uploader {

	override def upload(bytes: Array[Byte], contentType: Option[String], user: User, data: UploadFormSubmission): UploadResult = {
		val asset = uploadAndAudit(bytes, data.bucket, data.assetName, contentType, user)
		UploadResult(
			List(("Original", asset)),
			Nil,
			asset.bucket,
			asset.parent
		)
	}

}