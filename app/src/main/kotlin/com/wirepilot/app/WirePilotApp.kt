package com.wirepilot.app

import android.app.Application
import com.wireguard.android.backend.GoBackend
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.platform.AppContainer
import com.wirepilot.app.platform.AppLockLifecycle
import com.wirepilot.app.platform.AppCompatThemeMode

class WirePilotApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    AppCompatThemeMode.apply(container.themeModes.read())
    AppLockLifecycle(container.appLockSession).register(this)
    GoBackend.setAlwaysOnCallback {
      container.applyRunner.applyNow("always-on")
    }
    container.networkWatcher.register()
    container.logger.record(LogKind.NETWORK_CHANGE, "watcher registered")
    container.debouncer.scheduleProcessStartApply()
  }
}
