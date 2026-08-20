package com.wirepilot.app.data

data class StoredControl(
  val enabled: Boolean = true,
  val pausedUntilEpochMillis: Long? = null,
  val tunnelName: String = "",
  val excludedSsids: Set<String> = emptySet(),
  val connectOnMobile: Boolean = true,
)
