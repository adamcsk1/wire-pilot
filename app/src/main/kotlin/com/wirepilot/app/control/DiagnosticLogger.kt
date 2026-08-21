package com.wirepilot.app.control

import com.wirepilot.app.data.DiagnosticLogBuffer
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.LogEvent
import com.wirepilot.app.data.LogKind

class DiagnosticLogger(
  private val store: DiagnosticStore,
  private val clock: () -> Long,
) : DiagnosticLog {
  override fun record(kind: LogKind, detail: String) {
    val current = store.read()
    val next = DiagnosticLogBuffer.append(current, LogEvent(clock(), kind, detail))
    if (next != current) {
      store.write(next)
    }
  }
}
