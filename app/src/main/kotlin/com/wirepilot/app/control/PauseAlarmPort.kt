package com.wirepilot.app.control

interface PauseAlarmPort {
  fun schedule(atEpochMillis: Long)
  fun cancel()
}
