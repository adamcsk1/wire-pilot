package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEvaluatorTest {
  private val enabled = StoredControl(
    enabled = true,
    tunnelName = "office",
    excludedSsids = setOf("Home", "Guest"),
    mobileTunnelName = "office",
  )

  @Test
  fun downWhenControlDisabled() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(enabled = false),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN, "office"), decision)
  }

  @Test
  fun skipWhenControlDisabledAndTunnelBlank() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(enabled = false, tunnelName = "", mobileTunnelName = ""),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME), decision)
  }

  @Test
  fun skipWhenTunnelNameBlank() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(tunnelName = "  ", mobileTunnelName = ""),
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
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN, "office"), decision)
  }

  @Test
  fun downWhenAnyWifiSsidExcluded() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe", "Guest")),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN, "office"), decision)
  }

  @Test
  fun wifiStillTargetsDefaultWhenMobileDiffers() {
    val control = enabled.copy(mobileTunnelName = "travel")
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.UP, "office"),
      PolicyEvaluator.decide(control, NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe"))),
    )
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      PolicyEvaluator.decide(control, NetworkSnapshot(NetworkKind.WIFI, setOf("Home"))),
    )
  }

  @Test
  fun upWhenOnOtherSsid() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.UP, "office"), decision)
  }

  @Test
  fun upOnMobileWhenMobileTunnelSet() {
    val decision = PolicyEvaluator.decide(
      enabled,
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.UP, "office"), decision)
  }

  @Test
  fun downOnMobileWhenNoMobileTunnel() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(mobileTunnelName = ""),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.DOWN, "office"), decision)
  }

  @Test
  fun upOnMobileUsesMobileTunnelNotDefault() {
    val decision = PolicyEvaluator.decide(
      enabled.copy(mobileTunnelName = "travel"),
      NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyDecision.Apply(TunnelCommand.UP, "travel"), decision)
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
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      PolicyEvaluator.decide(enabled, NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), hasCellular = true)),
    )
  }

  @Test
  fun otherNetworkFollowsMobileTunnel() {
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.UP, "office"),
      PolicyEvaluator.decide(enabled, NetworkSnapshot(NetworkKind.OTHER)),
    )
    assertEquals(
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      PolicyEvaluator.decide(enabled.copy(mobileTunnelName = ""), NetworkSnapshot(NetworkKind.OTHER)),
    )
  }
}
