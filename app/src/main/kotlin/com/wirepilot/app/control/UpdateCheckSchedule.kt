package com.wirepilot.app.control

object UpdateCheckSchedule {
  const val INTERVAL_MS = 24L * 60L * 60L * 1000L

  fun isDue(lastCheckEpochMillis: Long, nowMillis: Long): Boolean {
    if (lastCheckEpochMillis <= 0L) {
      return true
    }
    return nowMillis - lastCheckEpochMillis >= INTERVAL_MS
  }

  fun nextAt(lastCheckEpochMillis: Long, nowMillis: Long): Long {
    if (lastCheckEpochMillis <= 0L) {
      return nowMillis + INTERVAL_MS
    }
    val dueAt = lastCheckEpochMillis + INTERVAL_MS
    return dueAt.coerceAtLeast(nowMillis)
  }
}
