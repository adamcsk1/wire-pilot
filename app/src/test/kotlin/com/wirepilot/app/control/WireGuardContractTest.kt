package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class WireGuardContractTest {
  @Test
  fun officialPackageAndPermission() {
    assertEquals("com.wireguard.android", WireGuardContract.PACKAGE_NAME)
    assertEquals("com.wireguard.android.permission.CONTROL_TUNNELS", WireGuardContract.PERMISSION)
    assertEquals("com.wireguard.android.action.SET_TUNNEL_UP", WireGuardContract.ACTION_SET_TUNNEL_UP)
    assertEquals("com.wireguard.android.action.SET_TUNNEL_DOWN", WireGuardContract.ACTION_SET_TUNNEL_DOWN)
    assertEquals("tunnel", WireGuardContract.EXTRA_TUNNEL)
    assertEquals(
      "com.wireguard.android.model.TunnelManager\$IntentReceiver",
      WireGuardContract.RECEIVER_CLASS_NAME,
    )
  }
}
