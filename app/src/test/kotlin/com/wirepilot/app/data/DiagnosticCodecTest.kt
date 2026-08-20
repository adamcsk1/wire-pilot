package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent
import com.wirepilot.app.control.LogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticCodecTest {
  @Test
  fun defaultWhenNullOrEmpty() {
    assertEquals(DiagnosticState(), DiagnosticCodec.decode(null))
    assertEquals(DiagnosticState(), DiagnosticCodec.decode(""))
  }

  @Test
  fun roundTrip() {
    val state = DiagnosticState(
      enabled = false,
      entries = listOf(
        LogEvent(10L, LogKind.BOOT, "boot"),
        LogEvent(20L, LogKind.APPLY, "result=up"),
      ),
    )
    assertEquals(state, DiagnosticCodec.decode(DiagnosticCodec.encode(state)))
  }

  @Test
  fun encodeEnabledHeaderOnly() {
    assertEquals("1", DiagnosticCodec.encode(DiagnosticState()))
    assertEquals("0", DiagnosticCodec.encode(DiagnosticState(enabled = false)))
  }

  @Test
  fun decodeSkipsBadLines() {
    val state = DiagnosticCodec.decode("1\nnot-a-number\tAPPLY\tx\n\n20\tNOPE\tx\n30\tAPPLY\tok")
    assertTrue(state.enabled)
    assertEquals(listOf(LogEvent(30L, LogKind.APPLY, "ok")), state.entries)
  }

  @Test
  fun sanitizeNewlinesAndTabs() {
    val decoded = DiagnosticCodec.decode(
      DiagnosticCodec.encode(DiagnosticState(entries = listOf(LogEvent(1L, LogKind.APPLY, "a\tb\nc")))),
    )
    assertEquals("a b c", decoded.entries.single().detail)
  }

  @Test
  fun decodeTwoFieldLineHasEmptyDetail() {
    val state = DiagnosticCodec.decode("0\n5\tBOOT")
    assertFalse(state.enabled)
    assertEquals(LogEvent(5L, LogKind.BOOT, ""), state.entries.single())
  }
}
