package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelIntentFactoryTest {
  @Test
  fun upIntentUsesOfficialActionComponentAndStoppedFlag() {
    val spec = TunnelIntentFactory.create("office", TunnelCommand.UP)
    assertEquals(WireGuardContract.ACTION_SET_TUNNEL_UP, spec.action)
    assertEquals(WireGuardContract.PACKAGE_NAME, spec.packageName)
    assertEquals(WireGuardContract.RECEIVER_CLASS_NAME, spec.receiverClassName)
    assertEquals(mapOf(WireGuardContract.EXTRA_TUNNEL to "office"), spec.extras)
    assertTrue(spec.includeStoppedPackages)
  }

  @Test
  fun downIntentUsesOfficialActionAndExtra() {
    val spec = TunnelIntentFactory.create("office", TunnelCommand.DOWN)
    assertEquals(WireGuardContract.ACTION_SET_TUNNEL_DOWN, spec.action)
    assertEquals(WireGuardContract.RECEIVER_CLASS_NAME, spec.receiverClassName)
    assertTrue(spec.includeStoppedPackages)
  }
}
