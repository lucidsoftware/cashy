package com.lucidchart.open.cashy.models

import javax.inject.Inject
import com.lucidchart.open.relate.interp._
import com.lucidchart.open.cashy.utils.Serializer

import play.api.db.Database
import com.google.api.client.auth.oauth2.StoredCredential


case class StoredCredentialRecord(
  key: String,
  value: Array[Byte]
)

object StoredCredentialModel extends StoredCredentialModel(play.api.Play.current.injector.instanceOf[Database])
class StoredCredentialModel @Inject() (db: Database) {

  def clearCredentials() {
    db.withConnection { implicit connection =>
      sql"""TRUNCATE TABLE `stored_credentials`""".execute()
    }
  }

  def containsValue(value: StoredCredential): Boolean = {
    val serializedValue = serializeCredential(value)

    db.withConnection { implicit connection =>
      val count = sql"""SELECT COUNT(*)
        FROM `stored_credentials`
        WHERE `value` = $serializedValue""".asSingle { row =>
          row.int("COUNT(*)")
      }
      count != 0
    }
  }

  def deleteKey(key: String) {
    db.withConnection { implicit connection =>
      sql"""DELETE FROM `stored_credentials`
        WHERE `key` = $key""".execute()
    }
  }

  def getByKey(key: String): Option[StoredCredential] = {
    db.withConnection { implicit connection =>
      val recordOption = sql"""SELECT `key`, `value`
        FROM `stored_credentials`
        WHERE `key` = $key""".asSingleOption { row =>
          StoredCredentialRecord(row.string("key"), row.byteArray("value"))
      }
      recordOption.flatMap(record => deserializeCredential(record.value))
    }
  }

  def getAllKeys(): Set[String] = {
    db.withConnection { implicit connection =>
      sql"""SELECT `key`
        FROM `stored_credentials`""".asSet { row =>
          row.string("key")
      }
    }
  }

  def getAllValues(): Set[StoredCredential] = {
    db.withConnection { implicit connection =>
      val byteSet = sql"""SELECT `value`
        FROM `stored_credentials`""".asSet { row =>
          row.byteArray("value")
      }
      byteSet.flatMap(bytes => deserializeCredential(bytes))
    }
  }

  def setCredential(key: String, value: StoredCredential) = {
    val serializedValue = serializeCredential(value)

    db.withConnection { implicit connection =>
      sql"""INSERT INTO `stored_credentials`
        VALUES($key, $serializedValue)
        ON DUPLICATE KEY UPDATE `value`=$serializedValue
        """.execute()
    }
  }

  def getSize(): Int = {
    db.withConnection { implicit connection =>
      sql"""SELECT COUNT(*)
        FROM `stored_credentials`""".asSingle { row =>
          row.int("COUNT(*)")
      }
    }
  }

  private def serializeCredential(credential: StoredCredential): Array[Byte] = {
    Serializer.objectToBytes(credential)
  }

  private def deserializeCredential(bytes: Array[Byte]): Option[StoredCredential] = {
    Serializer.bytesToObject[StoredCredential](bytes)
  }

}
