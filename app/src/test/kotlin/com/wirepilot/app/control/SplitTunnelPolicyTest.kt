package com.wirepilot.app.control

import com.wirepilot.app.data.SplitTunnelMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitTunnelPolicyTest {
  @Test
  fun allAppsClearsBothLists() {
    assertEquals(
      SplitTunnelSelection(emptySet(), emptySet()),
      SplitTunnelPolicy.selection(SplitTunnelMode.ALL_APPS, setOf("com.foo")),
    )
  }

  @Test
  fun excludeAndIncludeAreExclusive() {
    assertEquals(
      SplitTunnelSelection(setOf("com.foo"), emptySet()),
      SplitTunnelPolicy.selection(SplitTunnelMode.EXCLUDE_APPS, setOf(" com.foo ", "")),
    )
    assertEquals(
      SplitTunnelSelection(emptySet(), setOf("com.bar")),
      SplitTunnelPolicy.selection(SplitTunnelMode.INCLUDE_APPS, setOf("com.bar")),
    )
  }

  @Test
  fun modeFromListsPrefersInclude() {
    assertEquals(SplitTunnelMode.ALL_APPS, SplitTunnelPolicy.modeFrom(emptySet(), emptySet()))
    assertEquals(SplitTunnelMode.EXCLUDE_APPS, SplitTunnelPolicy.modeFrom(setOf("a"), emptySet()))
    assertEquals(SplitTunnelMode.INCLUDE_APPS, SplitTunnelPolicy.modeFrom(setOf("a"), setOf("b")))
  }
}
