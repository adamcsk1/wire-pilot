package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse

class TunnelStatePortTest {
  @Test
  fun noOpIsNeverUp() {
    assertFalse(NoOpTunnelState.isUp("office"))
    assertFalse(NoOpTunnelState.isUp(""))
  }
}
