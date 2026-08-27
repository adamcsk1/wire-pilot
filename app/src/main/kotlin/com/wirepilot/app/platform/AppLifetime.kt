package com.wirepilot.app.platform

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wireguard.android.backend.GoBackend

class AppLifetime(
  private val container: AppContainer,
) {
  fun start(application: Application) {
    AppCompatThemeMode.apply(container.themeModes.read())
    AppLockLifecycle(container.appLockSession).register(application)
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
    container.updateCheckRunner.reschedule()
  }
}
