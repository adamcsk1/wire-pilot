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
      policyEnabled = false,
      vpnEnabled = true,
      policyEntries = listOf(LogEvent(10L, LogKind.BOOT, "boot")),
      vpnEntries = listOf(LogEvent(20L, LogKind.TUNNEL, "up")),
    )
    assertEquals(state, DiagnosticCodec.decode(DiagnosticCodec.encode(state)))
  }

  @Test
  fun encodeEnabledHeaderOnly() {
    assertEquals("1\t1", DiagnosticCodec.encode(DiagnosticState()))
    assertEquals("0\t0", DiagnosticCodec.encode(DiagnosticState(policyEnabled = false, vpnEnabled = false)))
  }

  @Test
  fun decodeLegacyHeaderAndEvents() {
    val state = DiagnosticCodec.decode("0\n5\tBOOT")
    assertFalse(state.policyEnabled)
    assertTrue(state.vpnEnabled)
    assertEquals(LogEvent(5L, LogKind.BOOT, ""), state.policyEntries.single())
  }

  @Test
  fun decodeSkipsBadLines() {
    val state = DiagnosticCodec.decode("1\t1\nnot-a-number\tAPPLY\tPOLICY\tx\n\n20\tNOPE\tPOLICY\tx\n30\tAPPLY\tPOLICY\tok")
    assertTrue(state.policyEnabled)
    assertEquals(listOf(LogEvent(30L, LogKind.APPLY, "ok")), state.policyEntries)
  }

  @Test
  fun sanitizeNewlinesAndTabs() {
    val decoded = DiagnosticCodec.decode(
      DiagnosticCodec.encode(DiagnosticState(policyEntries = listOf(LogEvent(1L, LogKind.APPLY, "a\tb\nc")))),
    )
    assertEquals("a b c", decoded.policyEntries.single().detail)
  }
}
