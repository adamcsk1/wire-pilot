package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent

data class DiagnosticState(
  val enabled: Boolean = true,
  val entries: List<LogEvent> = emptyList(),
)
