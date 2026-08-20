package com.wirepilot.app.support

import com.wirepilot.app.data.DiagnosticState
import com.wirepilot.app.data.DiagnosticStore

class InMemoryDiagnosticStore(
  initial: DiagnosticState = DiagnosticState(),
) : DiagnosticStore {
  private var value: DiagnosticState = initial

  override fun read(): DiagnosticState = value

  override fun write(state: DiagnosticState) {
    value = state
  }
}
