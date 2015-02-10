package com.lucidchart.open.cashy.utils

import com.yahoo.platform.yui.compressor.CssCompressor
import scala.io.Codec
import java.io.{StringReader, StringWriter}

object CssCompress extends CssCompress
class CssCompress {

  def compress(bytes: Array[Byte]): Array[Byte] = {

    val cssString = new String(Codec.fromUTF8(bytes))

    val compressor = new CssCompressor(new StringReader(cssString))
    val output = new StringWriter()
    compressor.compress(output, 500)

    Codec.toUTF8(output.toString())
  }

}
