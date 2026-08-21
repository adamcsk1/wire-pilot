package com.wirepilot.app.data

data class LogEvent(
  val atMillis: Long,
  val kind: LogKind,
  val detail: String,
)
