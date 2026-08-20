package com.wirepilot.app

import android.app.Application
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.platform.AppContainer

class WirePilotApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    container.networkWatcher.register()
    container.logger.record(LogKind.NETWORK_CHANGE, "watcher registered")
    container.debouncer.scheduleProcessStartApply()
  }
}
