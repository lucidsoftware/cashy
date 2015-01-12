package com.lucidchart.open.cashy.utils

import java.io._
import scala.reflect.ClassManifest

// Slightly modified from http://stackoverflow.com/questions/22484765/binary-serialization-replacing-marshal-on-scala-2-10
object Serializer {
  def objectToBytes[T](obj: T): Array[Byte] = {
    val ba = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(ba)
    out.writeObject(obj)
    out.close()
    ba.toByteArray
  }

  def bytesToObject[T](bytes: Array[Byte]): Option[T] = {
    if (bytes != null) {
      try {
        val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
        val o = in.readObject.asInstanceOf[T]
        in.close()
        Some(o)
      }
      catch {
        case e: Exception => {
          throw new ClassCastException ("Serialization Problem")
          None
        }
      }
    }
    else {
      None
    }
  }
}