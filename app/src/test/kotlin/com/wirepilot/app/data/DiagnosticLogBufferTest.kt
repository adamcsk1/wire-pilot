package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent
import com.wirepilot.app.control.LogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticLogBufferTest {
  @Test
  fun appendIgnoredWhenDisabled() {
    val state = DiagnosticState(enabled = false)
    val next = DiagnosticLogBuffer.append(state, LogEvent(1L, LogKind.APPLY, "x"))
    assertEquals(state, next)
  }

  @Test
  fun appendKeepsLastMaxEntries() {
    var state = DiagnosticState()
    repeat(DiagnosticLogBuffer.MAX_ENTRIES + 5) { index ->
      state = DiagnosticLogBuffer.append(state, LogEvent(index.toLong(), LogKind.APPLY, index.toString()))
    }
    assertEquals(DiagnosticLogBuffer.MAX_ENTRIES, state.entries.size)
    assertEquals("5", state.entries.first().detail)
  }

  @Test
  fun clearKeepsEnabledFlag() {
    val cleared = DiagnosticLogBuffer.clear(
      DiagnosticState(enabled = false, entries = listOf(LogEvent(1L, LogKind.BOOT, "x"))),
    )
    assertFalse(cleared.enabled)
    assertEquals(emptyList(), cleared.entries)
  }

  @Test
  fun setEnabled() {
    assertFalse(DiagnosticLogBuffer.setEnabled(DiagnosticState(), false).enabled)
  }
}
