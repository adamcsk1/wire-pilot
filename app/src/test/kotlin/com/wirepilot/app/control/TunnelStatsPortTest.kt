package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertNull

class TunnelStatsPortTest {
  @Test
  fun noOpReturnsNull() {
    assertNull(NoOpTunnelStats.traffic("office"))
    assertNull(NoOpTunnelStats.traffic(""))
  }
}
