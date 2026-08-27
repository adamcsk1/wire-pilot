package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.StoredControl

enum class NetworkMonitorMode {
  STOPPED,
  WATCHING,
  PAUSED,
}

object NetworkMonitorPolicy {
  fun mode(control: StoredControl, nowMillis: Long): NetworkMonitorMode {
    val resolved = ControlModeResolver.resolve(control, nowMillis)
    if (resolved.tunnelName.isBlank()) {
      return NetworkMonitorMode.STOPPED
    }
    if (resolved.enabled) {
      return NetworkMonitorMode.WATCHING
    }
    return if (resolved.pausedUntilEpochMillis != null) {
      NetworkMonitorMode.PAUSED
    } else {
      NetworkMonitorMode.STOPPED
    }
  }

  fun shouldScheduleDebouncedApply(control: StoredControl, nowMillis: Long): Boolean {
    return mode(control, nowMillis) != NetworkMonitorMode.STOPPED
  }
}

class NetworkMonitorCoordinator(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val applyMode: (NetworkMonitorMode, Boolean) -> Unit,
) {
  fun reconcile() {
    applyMode(currentMode(), true)
  }

  fun reconcileWithoutServiceStart() {
    applyMode(currentMode(), false)
  }

  fun currentMode(): NetworkMonitorMode {
    val nowMillis = clock()
    val current = store.read()
    val resolved = ControlModeResolver.resolve(current, nowMillis)
    if (resolved != current) {
      store.write(resolved)
    }
    return NetworkMonitorPolicy.mode(resolved, nowMillis)
  }
}
