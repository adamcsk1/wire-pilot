package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEvaluatorTest {
  private val enabled = StoredControl(
    enabled = true,
    tunnelName = "office",
    excludedSsids = setOf("Home", "Guest"),
  )

  @Test
  fun skipWhenControlDisabled() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(enabled = false),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Skip(SkipReason.CONTROL_DISABLED), decision)
  }

  @Test
  fun skipWhenTunnelNameBlank() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(tunnelName = "  "),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME), decision)
  }

  @Test
  fun skipWhenWifiSsidUnreadable() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI),
    )
    assertEquals(PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE), decision)
  }

  @Test
  fun downWhenOnExcludedSsid() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI, setOf("Home")),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN), decision)
  }

  @Test
  fun downWhenAnyWifiSsidExcluded() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe", "Guest")),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN), decision)
  }

  @Test
  fun upWhenOnOtherSsid() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.UP), decision)
  }

  @Test
  fun upOnMobileWhenFlagOn() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.UP), decision)
  }

  @Test
  fun skipOnMobileWhenFlagOff() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(connectOnMobile = false),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Skip(SkipReason.MOBILE_DISABLED), decision)
  }

  @Test
  fun settlingAlwaysSkipsEvenWithCellular() {
    assertEquals(
      PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE),
      PolicyEvaluator.decide(enabled, NetworkSnapshot(NetworkKind.WIFI_SETTLING, hasCellular = true)),
    )
  }

  @Test
  fun excludedWifiAfterSettleIsDown() {
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.DOWN),
      PolicyEvaluator.decide(enabled, NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), hasCellular = true)),
    )
  }

  @Test
  fun otherNetworkFollowsMobileFlag() {
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.UP),
      PolicyEvaluator.decide(enabled, NetworkSnapshot(NetworkKind.OTHER)),
    )
    assertEquals(
      PolicyDecision.Skip(SkipReason.MOBILE_DISABLED),
      PolicyEvaluator.decide(enabled.copy(connectOnMobile = false), NetworkSnapshot(NetworkKind.OTHER)),
    )
  }
}
