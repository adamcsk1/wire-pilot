package com.wirepilot.app.support

import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.LogKind

class RecordingLog : DiagnosticLog {
  val entries = mutableListOf<Pair<LogKind, String>>()

  override fun record(kind: LogKind, detail: String) {
    entries += kind to detail
  }
}
