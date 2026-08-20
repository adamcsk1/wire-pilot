package com.wirepilot.app.control

data class LogEvent(
  val atMillis: Long,
  val kind: LogKind,
  val detail: String,
)
