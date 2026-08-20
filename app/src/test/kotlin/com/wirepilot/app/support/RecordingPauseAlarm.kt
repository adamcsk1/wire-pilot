package com.wirepilot.app.support

import com.wirepilot.app.control.PauseAlarmPort

class RecordingPauseAlarm : PauseAlarmPort {
  var scheduledAt: Long? = null
  var cancelCount: Int = 0

  override fun schedule(atEpochMillis: Long) {
    scheduledAt = atEpochMillis
  }

  override fun cancel() {
    scheduledAt = null
    cancelCount += 1
  }
}
