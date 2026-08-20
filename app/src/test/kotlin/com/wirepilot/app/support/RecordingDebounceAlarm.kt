package com.wirepilot.app.support

import com.wirepilot.app.control.DebounceAlarmPort

class RecordingDebounceAlarm : DebounceAlarmPort {
  var scheduleCount: Int = 0

  override fun scheduleDebouncedApply() {
    scheduleCount += 1
  }
}
