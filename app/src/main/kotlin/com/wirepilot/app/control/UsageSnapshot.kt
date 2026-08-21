package com.wirepilot.app.control

data class UsageSnapshot(
  val enabled: Boolean,
  val connected: Boolean = false,
  val tunnelName: String = "",
  val rxBytes: Long = 0L,
  val txBytes: Long = 0L,
)
