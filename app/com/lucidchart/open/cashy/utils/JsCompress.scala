package com.lucidchart.open.cashy.utils

import com.yahoo.platform.yui.compressor.JavaScriptCompressor
import org.mozilla.javascript.{ErrorReporter, EvaluatorException}
import scala.io.Codec
import java.io.{StringReader, StringWriter}
import scala.collection.mutable.ListBuffer

class CashyErrorReporter extends ErrorReporter {
  val errors: ListBuffer[String] = new ListBuffer()

  // Ignore warnings
  override def warning(
      message: String,
      sourceName: String,
      line: Int,
      lineSource: String,
      lineOffset: Int
  ): Unit = {}

  override def error(
      message: String,
      sourceName: String,
      line: Int,
      lineSource: String,
      lineOffset: Int
  ): Unit = {
    errors += s"""ERROR: $line:$lineOffset $message"""
  }

  override def runtimeError(
      message: String,
      sourceName: String,
      line: Int,
      lineSource: String,
      lineOffset: Int
  ): EvaluatorException = {
    val exceptionMessage = s"""$line:$lineOffset $message"""
    new EvaluatorException(exceptionMessage)
  }
}

object JsCompress extends JsCompress
class JsCompress {

  def compress(bytes: Array[Byte]): (Array[Byte], List[String]) = {

    val jsString = new String(Codec.fromUTF8(bytes))
    val errorReporter = new CashyErrorReporter()

    val compressor = new JavaScriptCompressor(new StringReader(jsString), errorReporter)
    val output = new StringWriter()
    compressor.compress(output, 500, true, true, true, true)

    (Codec.toUTF8(output.toString()), errorReporter.errors.toList)
  }

}
