package com.wirepilot.app.control

object DebouncePolicy {
  const val WINDOW_MS = 3_000L

  fun nextFireAt(nowMillis: Long): Long = nowMillis + WINDOW_MS

  fun isDue(scheduledAtMillis: Long, nowMillis: Long): Boolean = nowMillis >= scheduledAtMillis
}
