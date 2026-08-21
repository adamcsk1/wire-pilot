package com.wirepilot.app.data

data class DiagnosticState(
  val policyEnabled: Boolean = false,
  val vpnEnabled: Boolean = false,
  val usageEnabled: Boolean = false,
  val policyEntries: List<LogEvent> = emptyList(),
  val vpnEntries: List<LogEvent> = emptyList(),
)
