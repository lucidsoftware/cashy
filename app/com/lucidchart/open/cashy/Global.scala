package com.lucidchart.open.cashy

import com.lucidchart.open.cashy.amazons3.S3Sync
import play.api.GlobalSettings

object Global extends GlobalSettings {

  override def onStart(app: play.api.Application) {
    S3Sync.schedule

    super.onStart(app)
  }
}