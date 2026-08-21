package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl

object PolicyEvaluator {
  fun decide(
    control: StoredControl,
    network: NetworkSnapshot,
  ): PolicyDecision {
    val defaultName = control.tunnelName.trim()
    val mobileName = control.mobileTunnelName.trim()
    if (defaultName.isBlank() && mobileName.isBlank()) {
      return PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME)
    }
    if (!control.enabled) {
      return PolicyDecision.Apply(TunnelCommand.DOWN, defaultName.ifBlank { mobileName })
    }
    return when (network.kind) {
      NetworkKind.WIFI -> decideWifi(control.copy(tunnelName = defaultName), network)
      NetworkKind.WIFI_SETTLING ->
        if (defaultName.isBlank()) {
          PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME)
        } else {
          PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE)
        }
      NetworkKind.MOBILE, NetworkKind.OTHER -> decideMobile(defaultName, mobileName)
    }
  }

  private fun decideWifi(control: StoredControl, network: NetworkSnapshot): PolicyDecision {
    if (control.tunnelName.isBlank()) {
      return PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME)
    }
    if (network.wifiSsids.isEmpty()) {
      return PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE)
    }
    if (network.wifiSsids.any { it in control.excludedSsids }) {
      return PolicyDecision.Apply(TunnelCommand.DOWN, control.tunnelName)
    }
    return PolicyDecision.Apply(TunnelCommand.UP, control.tunnelName)
  }

  private fun decideMobile(defaultName: String, mobileName: String): PolicyDecision {
    if (mobileName.isBlank()) {
      if (defaultName.isBlank()) {
        return PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME)
      }
      return PolicyDecision.Apply(TunnelCommand.DOWN, defaultName)
    }
    return PolicyDecision.Apply(TunnelCommand.UP, mobileName)
  }
}
