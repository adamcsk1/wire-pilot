package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyLinePresenterTest {
  @Test
  fun blankTunnelWinsOverDisabled() {
    assertEquals(
      PolicyLine(PolicyLineKind.NO_TUNNEL),
      PolicyLinePresenter.present(
        StatusPresentation.Disabled,
        PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
  }

  @Test
  fun controlOffWhenDisabled() {
    assertEquals(
      PolicyLine(PolicyLineKind.CONTROL_OFF),
      PolicyLinePresenter.present(
        StatusPresentation.Disabled,
        PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
  }

  @Test
  fun pausedIgnoresNetwork() {
    assertEquals(
      PolicyLine(PolicyLineKind.PAUSED),
      PolicyLinePresenter.present(
        StatusPresentation.Paused(10L),
        PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
        NetworkSnapshot(NetworkKind.WIFI, setOf("Home")),
      ),
    )
  }

  @Test
  fun unreadableWifi() {
    assertEquals(
      PolicyLine(PolicyLineKind.WIFI_UNREADABLE),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE),
        NetworkSnapshot(NetworkKind.WIFI),
      ),
    )
  }

  @Test
  fun excludedWifiUsesMatchingSsid() {
    assertEquals(
      PolicyLine(PolicyLineKind.WIFI_EXCLUDED_DOWN, "office", "Home"),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
        NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe", "Home")),
        excludedSsids = setOf("Home"),
      ),
    )
  }

  @Test
  fun wifiUp() {
    assertEquals(
      PolicyLine(PolicyLineKind.WIFI_UP, "office", "Cafe"),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Apply(TunnelCommand.UP, "office"),
        NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")),
      ),
    )
  }

  @Test
  fun wifiUpLastKnown() {
    assertEquals(
      PolicyLine(PolicyLineKind.WIFI_UP_LAST_KNOWN, "office", "Home"),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Apply(TunnelCommand.UP, "office"),
        NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), ssidSource = PolicyLinePresenter.LAST_KNOWN_SOURCE),
      ),
    )
  }

  @Test
  fun mobileUpAndDown() {
    assertEquals(
      PolicyLine(PolicyLineKind.MOBILE_UP, "travel"),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Apply(TunnelCommand.UP, "travel"),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
    assertEquals(
      PolicyLine(PolicyLineKind.MOBILE_DOWN, "office"),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
  }

  @Test
  fun leftoverSkipReasons() {
    assertEquals(
      PolicyLine(PolicyLineKind.CONTROL_OFF),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Skip(SkipReason.CONTROL_DISABLED),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
    assertEquals(
      PolicyLine(PolicyLineKind.MOBILE_DOWN),
      PolicyLinePresenter.present(
        StatusPresentation.Watching,
        PolicyDecision.Skip(SkipReason.MOBILE_DISABLED),
        NetworkSnapshot(NetworkKind.MOBILE),
      ),
    )
  }
}
