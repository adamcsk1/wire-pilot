package com.wirepilot.app.data

import com.wirepilot.app.data.SplitTunnelMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitTunnelCodecTest {
  @Test
  fun roundTrip() {
    val stored = StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("b", "a"))
    assertEquals(stored, SplitTunnelCodec.decode(SplitTunnelCodec.encode(stored)))
  }

  @Test
  fun blankIsAllApps() {
    assertEquals(StoredSplitTunnel(), SplitTunnelCodec.decode(null))
    assertEquals(StoredSplitTunnel(), SplitTunnelCodec.decode(""))
    assertEquals(StoredSplitTunnel(), SplitTunnelCodec.decode("NOPE"))
  }
}
