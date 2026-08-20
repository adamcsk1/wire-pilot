package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent

data class DiagnosticState(
  val policyEnabled: Boolean = true,
  val vpnEnabled: Boolean = true,
  val policyEntries: List<LogEvent> = emptyList(),
  val vpnEntries: List<LogEvent> = emptyList(),
)
