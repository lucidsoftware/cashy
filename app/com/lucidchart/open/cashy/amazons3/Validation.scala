package com.lucidchart.open.cashy.amazons3

object Validation {

  // http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html
  // plus @, for backwards compatibility
  private val filenameRegex = """[0-9a-zA-Z!-_\.*'()@/]+""".r

  def isSafeS3Key(key: String) = filenameRegex.unapplySeq(key).isDefined

}
