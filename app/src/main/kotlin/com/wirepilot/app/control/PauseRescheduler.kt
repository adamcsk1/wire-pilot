package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore

class PauseRescheduler(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val pauseAlarms: PauseAlarmPort,
) {
  fun rescheduleIfNeeded(): Boolean {
    val control = store.read()
    val pauseUntil = control.pausedUntilEpochMillis
    if (!control.enabled && pauseUntil != null && clock() < pauseUntil) {
      pauseAlarms.schedule(pauseUntil)
      return true
    }
    return false
  }
}
