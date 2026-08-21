package com.wirepilot.app.data

import com.wirepilot.app.data.LogEvent
import com.wirepilot.app.data.LogKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticLogBufferTest {
  @Test
  fun appendIgnoredWhenPolicyDisabled() {
    val state = DiagnosticState(policyEnabled = false)
    val next = DiagnosticLogBuffer.append(state, LogEvent(1L, LogKind.APPLY, "x"))
    assertEquals(state, next)
  }

  @Test
  fun appendIgnoredWhenVpnDisabled() {
    val state = DiagnosticState(vpnEnabled = false)
    val next = DiagnosticLogBuffer.append(state, LogEvent(1L, LogKind.TUNNEL, "up"))
    assertEquals(state, next)
  }

  @Test
  fun appendKeepsLastMaxPolicyEntries() {
    var state = DiagnosticState(policyEnabled = true)
    repeat(DiagnosticLogBuffer.MAX_ENTRIES + 5) { index ->
      state = DiagnosticLogBuffer.append(state, LogEvent(index.toLong(), LogKind.APPLY, index.toString()))
    }
    assertEquals(DiagnosticLogBuffer.MAX_ENTRIES, state.policyEntries.size)
    assertEquals("5", state.policyEntries.first().detail)
  }

  @Test
  fun appendRoutesTunnelToVpn() {
    val next = DiagnosticLogBuffer.append(DiagnosticState(vpnEnabled = true), LogEvent(1L, LogKind.TUNNEL_ERROR, "x"))
    assertEquals(1, next.vpnEntries.size)
    assertTrue(next.policyEntries.isEmpty())
  }

  @Test
  fun clearKeepsEnabledFlags() {
    val cleared = DiagnosticLogBuffer.clear(
      DiagnosticState(policyEnabled = false, vpnEnabled = true, policyEntries = listOf(LogEvent(1L, LogKind.BOOT, "x"))),
    )
    assertFalse(cleared.policyEnabled)
    assertTrue(cleared.vpnEnabled)
    assertEquals(emptyList(), cleared.policyEntries)
  }

  @Test
  fun setEnabledFlags() {
    assertFalse(DiagnosticLogBuffer.setPolicyEnabled(DiagnosticState(), false).policyEnabled)
    assertFalse(DiagnosticLogBuffer.setVpnEnabled(DiagnosticState(), false).vpnEnabled)
    val both = DiagnosticLogBuffer.setEnabled(DiagnosticState(), false)
    assertFalse(both.policyEnabled)
    assertFalse(both.vpnEnabled)
    assertTrue(DiagnosticLogBuffer.clearPolicy(DiagnosticState(policyEntries = listOf(LogEvent(1, LogKind.BOOT, "x")))).policyEntries.isEmpty())
    assertTrue(DiagnosticLogBuffer.clearVpn(DiagnosticState(vpnEntries = listOf(LogEvent(1, LogKind.TUNNEL, "x")))).vpnEntries.isEmpty())
    assertTrue(DiagnosticLogBuffer.setUsageEnabled(DiagnosticState(), true).usageEnabled)
  }
}
