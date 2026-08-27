package com.wirepilot.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wireguard.android.backend.GoBackend
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
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        container.networkWatcher.restartLive()
      }
    })
    GoBackend.setAlwaysOnCallback {
      container.debouncer.scheduleDebouncedApply()
    }
    container.networkMonitorCoordinator.reconcile()
    container.debouncer.scheduleProcessStartApply()
  }
}
