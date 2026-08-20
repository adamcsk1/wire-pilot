package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl

object ControlModeResolver {
  fun resolve(control: StoredControl, nowMillis: Long): StoredControl {
    if (control.enabled) {
      if (control.pausedUntilEpochMillis != null) {
        return control.copy(pausedUntilEpochMillis = null)
      }
      return control
    }
    val pauseUntil = control.pausedUntilEpochMillis ?: return control
    if (nowMillis >= pauseUntil) {
      return control.copy(enabled = true, pausedUntilEpochMillis = null)
    }
    return control
  }
}
