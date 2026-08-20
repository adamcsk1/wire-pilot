package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl

object PauseCalculator {
  fun apply(control: StoredControl, option: PauseOption, nowMillis: Long): StoredControl {
    val duration = option.durationMillis
    return if (duration == null) {
      control.copy(enabled = false, pausedUntilEpochMillis = null)
    } else {
      control.copy(enabled = false, pausedUntilEpochMillis = nowMillis + duration)
    }
  }

  fun resume(control: StoredControl): StoredControl {
    return control.copy(enabled = true, pausedUntilEpochMillis = null)
  }
}
