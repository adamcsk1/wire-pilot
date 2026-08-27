package com.wirepilot.app.data

data class StoredControl(
  val enabled: Boolean = false,
  val pausedUntilEpochMillis: Long? = null,
  val tunnelName: String = "",
  val excludedSsids: Set<String> = emptySet(),
  val mobileTunnelName: String = "",
)
