package com.wirepilot.app.control

enum class PolicyLineKind {
  CONTROL_OFF,
  CONTROL_OFF_CONNECTED,
  PAUSED,
  PAUSED_CONNECTED,
  NO_TUNNEL,
  WIFI_UNREADABLE,
  WIFI_EXCLUDED_DOWN,
  WIFI_UP,
  WIFI_UP_LAST_KNOWN,
  MOBILE_UP,
  MOBILE_DOWN,
}

data class PolicyLine(
  val kind: PolicyLineKind,
  val tunnelName: String = "",
  val ssid: String = "",
)

object PolicyLinePresenter {
  fun present(
    status: StatusPresentation,
    decision: PolicyDecision,
    network: NetworkSnapshot,
    excludedSsids: Set<String> = emptySet(),
    vpnConnected: Boolean = false,
    connectedTunnelName: String = "",
  ): PolicyLine {
    if (isBlankTunnel(decision)) {
      return PolicyLine(PolicyLineKind.NO_TUNNEL)
    }
    when (status) {
      StatusPresentation.Disabled -> return offOrPausedLine(
        connected = vpnConnected,
        connectedKind = PolicyLineKind.CONTROL_OFF_CONNECTED,
        downKind = PolicyLineKind.CONTROL_OFF,
        connectedTunnelName = connectedTunnelName,
      )
      is StatusPresentation.Paused -> return offOrPausedLine(
        connected = vpnConnected,
        connectedKind = PolicyLineKind.PAUSED_CONNECTED,
        downKind = PolicyLineKind.PAUSED,
        connectedTunnelName = connectedTunnelName,
      )
      StatusPresentation.Watching -> Unit
    }
    return when (decision) {
      is PolicyDecision.Skip -> skipLine(decision.reason)
      is PolicyDecision.Apply -> applyLine(decision, network, excludedSsids)
    }
  }

  private fun offOrPausedLine(
    connected: Boolean,
    connectedKind: PolicyLineKind,
    downKind: PolicyLineKind,
    connectedTunnelName: String,
  ): PolicyLine {
    return if (connected) {
      PolicyLine(connectedKind, connectedTunnelName)
    } else {
      PolicyLine(downKind)
    }
  }

  private fun isBlankTunnel(decision: PolicyDecision): Boolean {
    return decision is PolicyDecision.Skip && decision.reason == SkipReason.BLANK_TUNNEL_NAME
  }

  private fun skipLine(reason: SkipReason): PolicyLine {
    return when (reason) {
      SkipReason.WIFI_SSID_UNREADABLE -> PolicyLine(PolicyLineKind.WIFI_UNREADABLE)
      SkipReason.BLANK_TUNNEL_NAME -> PolicyLine(PolicyLineKind.NO_TUNNEL)
      SkipReason.CONTROL_DISABLED -> PolicyLine(PolicyLineKind.CONTROL_OFF)
      SkipReason.MOBILE_DISABLED -> PolicyLine(PolicyLineKind.MOBILE_DOWN)
    }
  }

  private fun applyLine(
    decision: PolicyDecision.Apply,
    network: NetworkSnapshot,
    excludedSsids: Set<String>,
  ): PolicyLine {
    val onWifi = network.kind == NetworkKind.WIFI
    if (decision.command == TunnelCommand.UP) {
      if (onWifi) {
        val kind = if (network.ssidSource == LAST_KNOWN_SOURCE) {
          PolicyLineKind.WIFI_UP_LAST_KNOWN
        } else {
          PolicyLineKind.WIFI_UP
        }
        return PolicyLine(kind, decision.tunnelName, displaySsid(network.wifiSsids))
      }
      return PolicyLine(PolicyLineKind.MOBILE_UP, decision.tunnelName)
    }
    if (onWifi) {
      val skipped = network.wifiSsids.filter { ssid -> ssid in excludedSsids }.toSet()
      return PolicyLine(
        PolicyLineKind.WIFI_EXCLUDED_DOWN,
        decision.tunnelName,
        displaySsid(skipped.ifEmpty { network.wifiSsids }),
      )
    }
    return PolicyLine(PolicyLineKind.MOBILE_DOWN, decision.tunnelName)
  }

  private fun displaySsid(ssids: Set<String>): String {
    return ssids.sorted().firstOrNull().orEmpty()
  }

  const val LAST_KNOWN_SOURCE = "lastKnown"
}
