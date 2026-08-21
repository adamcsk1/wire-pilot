package com.wirepilot.app.control

import com.wirepilot.app.data.LogKind

fun interface DiagnosticLog {
  fun record(kind: LogKind, detail: String)
}

object NoOpDiagnosticLog : DiagnosticLog {
  override fun record(kind: LogKind, detail: String) = Unit
}
