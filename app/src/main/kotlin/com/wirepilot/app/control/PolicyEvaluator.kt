package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl

object PolicyEvaluator {
  fun decide(
    control: StoredControl,
    network: NetworkSnapshot,
  ): PolicyDecision {
    if (!control.enabled) {
      return PolicyDecision.Skip(SkipReason.CONTROL_DISABLED)
    }
    if (control.tunnelName.isBlank()) {
      return PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME)
    }
    return when (network.kind) {
      NetworkKind.WIFI -> decideWifi(control, network)
      NetworkKind.WIFI_SETTLING -> PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE)
      NetworkKind.MOBILE, NetworkKind.OTHER -> decideMobile(control)
    }
  }

  private fun decideWifi(control: StoredControl, network: NetworkSnapshot): PolicyDecision {
    if (network.wifiSsids.isEmpty()) {
      return PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE)
    }
    if (network.wifiSsids.any { it in control.excludedSsids }) {
      return PolicyDecision.Apply(TunnelCommand.DOWN)
    }
    return PolicyDecision.Apply(TunnelCommand.UP)
  }

  private fun decideMobile(control: StoredControl): PolicyDecision {
    if (!control.connectOnMobile) {
      return PolicyDecision.Skip(SkipReason.MOBILE_DISABLED)
    }
    return PolicyDecision.Apply(TunnelCommand.UP)
  }
}
