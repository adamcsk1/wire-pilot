package com.wirepilot.app.control

fun interface DiagnosticLog {
  fun record(kind: LogKind, detail: String)
}

object NoOpDiagnosticLog : DiagnosticLog {
  override fun record(kind: LogKind, detail: String) = Unit
}
