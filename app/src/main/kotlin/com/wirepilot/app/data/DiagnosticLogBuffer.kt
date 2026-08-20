package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent

object DiagnosticLogBuffer {
  const val MAX_ENTRIES = 150

  fun append(state: DiagnosticState, event: LogEvent): DiagnosticState {
    if (!state.enabled) {
      return state
    }
    return state.copy(entries = (state.entries + event).takeLast(MAX_ENTRIES))
  }

  fun clear(state: DiagnosticState): DiagnosticState {
    return state.copy(entries = emptyList())
  }

  fun setEnabled(state: DiagnosticState, enabled: Boolean): DiagnosticState {
    return state.copy(enabled = enabled)
  }
}
