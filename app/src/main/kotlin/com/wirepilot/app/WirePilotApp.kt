package com.wirepilot.app

import android.app.Application
import com.wireguard.android.backend.GoBackend
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.platform.AppContainer
import com.wirepilot.app.platform.AppLockLifecycle

class WirePilotApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    AppLockLifecycle(container.appLockSession).register(this)
    GoBackend.setAlwaysOnCallback {
      container.applyRunner.applyNow("always-on")
    }
    container.networkWatcher.register()
    container.logger.record(LogKind.NETWORK_CHANGE, "watcher registered")
    container.debouncer.scheduleProcessStartApply()
  }
}
