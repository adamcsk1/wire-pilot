package com.wirepilot.app

import android.app.Application
import com.wirepilot.app.platform.AppContainer
import com.wirepilot.app.platform.AppLifetime

class WirePilotApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    AppLifetime(container).start(this)
  }
}
