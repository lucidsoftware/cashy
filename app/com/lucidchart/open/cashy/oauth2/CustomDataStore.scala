package com.lucidchart.open.cashy.oauth2

import javax.inject.Inject
import com.google.api.client.util.store.{DataStoreFactory, AbstractDataStore, DataStore}
import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import scala.jdk.CollectionConverters._

import com.lucidchart.open.cashy.models.StoredCredentialModel

class CustomDataStore @Inject() (storedCredentialModel: StoredCredentialModel)
    extends DataStore[StoredCredential] {

  def clear = {
    storedCredentialModel.clearCredentials()
    this
  }

  def containsKey(key: String): Boolean = {
    get(key) != null
  }

  def containsValue(value: StoredCredential): Boolean = {
    storedCredentialModel.containsValue(value)
  }

  def delete(key: String) = {
    storedCredentialModel.deleteKey(key)
    this
  }

  def get(key: String): StoredCredential = {
    storedCredentialModel.getByKey(key).getOrElse(null)
  }

  // Returns the factory used to create the data store
  def getDataStoreFactory() = {
    null
  }

  // Returns the id of the data store
  def getId(): String = {
    "datastoreid"
  }

  def isEmpty(): Boolean = {
    size == 0
  }

  def keySet() = {
    storedCredentialModel.getAllKeys().asJava
  }

  def set(key: String, value: StoredCredential) = {
    storedCredentialModel.setCredential(key, value)
    this
  }

  def size(): Int = {
    storedCredentialModel.getSize()
  }

  def values() = {
    storedCredentialModel.getAllValues().asJava
  }

}
