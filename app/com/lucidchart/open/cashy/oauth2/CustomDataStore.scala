package com.lucidchart.open.cashy.oauth2

import com.google.api.client.util.store.{DataStoreFactory, AbstractDataStore, DataStore}
import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import scala.collection.JavaConverters._

import com.lucidchart.open.cashy.models.StoredCredentialModel

object CustomDataStore extends CustomDataStore
class CustomDataStore extends DataStore[StoredCredential] {

  def clear = {
    StoredCredentialModel.clearCredentials()
    CustomDataStore
  }

  def containsKey(key: String): Boolean = {
    get(key) != null
  }

  def containsValue(value: StoredCredential): Boolean = {
    StoredCredentialModel.containsValue(value)
  }

  def delete(key: String) = {
    StoredCredentialModel.deleteKey(key)
    CustomDataStore
  }

  def get(key: String): StoredCredential = {
    StoredCredentialModel.getByKey(key).getOrElse(null)
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
    StoredCredentialModel.getAllKeys().asJava
  }

  def set(key: String, value: StoredCredential) = {
    StoredCredentialModel.setCredential(key, value)
    CustomDataStore
  }

  def size(): Int = {
    StoredCredentialModel.getSize()
  }

  def values() = {
    StoredCredentialModel.getAllValues().asJava
  }

}
