package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LogChannelsTest {
  @Test
  fun tunnelKindsAreVpn() {
    assertEquals(LogChannel.VPN, LogChannels.of(LogKind.TUNNEL))
    assertEquals(LogChannel.VPN, LogChannels.of(LogKind.TUNNEL_ERROR))
  }

  @Test
  fun otherKindsArePolicy() {
    assertEquals(LogChannel.POLICY, LogChannels.of(LogKind.APPLY))
    assertEquals(LogChannel.POLICY, LogChannels.of(LogKind.NETWORK_CHANGE))
    assertEquals(LogChannel.POLICY, LogChannels.of(LogKind.WATCHING))
  }
}
