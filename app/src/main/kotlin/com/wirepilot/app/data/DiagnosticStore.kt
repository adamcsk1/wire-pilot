package com.wirepilot.app.data

interface DiagnosticStore {
  fun read(): DiagnosticState
  fun write(state: DiagnosticState)
}
