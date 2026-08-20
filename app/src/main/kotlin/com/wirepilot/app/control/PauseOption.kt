package com.wirepilot.app.control

enum class PauseOption(val durationMillis: Long?) {
  HOURS_1(1L * 60L * 60L * 1000L),
  HOURS_2(2L * 60L * 60L * 1000L),
  HOURS_4(4L * 60L * 60L * 1000L),
  HOURS_8(8L * 60L * 60L * 1000L),
  HOURS_12(12L * 60L * 60L * 1000L),
  HOURS_24(24L * 60L * 60L * 1000L),
  ALWAYS(null),
}
