package com.wirepilot.app.control

import com.wirepilot.app.data.DiagnosticState
import com.wirepilot.app.support.InMemoryDiagnosticStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticLoggerTest {
  @Test
  fun recordsWhenEnabled() {
    val store = InMemoryDiagnosticStore(DiagnosticState(policyEnabled = true))
    DiagnosticLogger(store) { 42L }.record(LogKind.BOOT, "start")
    assertEquals(listOf(LogEvent(42L, LogKind.BOOT, "start")), store.read().policyEntries)
  }

  @Test
  fun doesNotWriteWhenDisabled() {
    val store = InMemoryDiagnosticStore(DiagnosticState(policyEnabled = false))
    var writes = 0
    val counting = object : com.wirepilot.app.data.DiagnosticStore {
      override fun read() = store.read()
      override fun write(state: DiagnosticState) {
        writes += 1
        store.write(state)
      }
    }
    DiagnosticLogger(counting) { 1L }.record(LogKind.APPLY, "x")
    assertEquals(0, writes)
    assertTrue(store.read().policyEntries.isEmpty())
  }
}
