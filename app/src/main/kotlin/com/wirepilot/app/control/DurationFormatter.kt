package com.wirepilot.app.control

object DurationFormatter {
  fun remainingHoursAndMinutes(remainingMillis: Long): Pair<Long, Long> {
    val safe = remainingMillis.coerceAtLeast(0L)
    val totalMinutes = safe / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return hours to minutes
  }
}
