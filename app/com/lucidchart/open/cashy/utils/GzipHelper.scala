package com.lucidchart.open.cashy.utils


import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import scala.concurrent.ExecutionContext.Implicits.global

object GzipHelper extends GzipHelper
class GzipHelper {
  def compress(bytes: Array[Byte]): Array[Byte] =  {
    val baos = new ByteArrayOutputStream
    val gzos = new GZIPOutputStream(baos)
    gzos.write(bytes)
    gzos.finish
    gzos.close
    baos.close
    baos.toByteArray
  }
}