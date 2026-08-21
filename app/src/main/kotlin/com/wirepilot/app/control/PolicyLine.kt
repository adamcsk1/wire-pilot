package com.wirepilot.app.control

enum class PolicyLineKind {
  CONTROL_OFF,
  PAUSED,
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
  ): PolicyLine {
    if (isBlankTunnel(decision)) {
      return PolicyLine(PolicyLineKind.NO_TUNNEL)
    }
    when (status) {
      StatusPresentation.Disabled -> return PolicyLine(PolicyLineKind.CONTROL_OFF)
      is StatusPresentation.Paused -> return PolicyLine(PolicyLineKind.PAUSED)
      StatusPresentation.Watching -> Unit
    }
    return when (decision) {
      is PolicyDecision.Skip -> skipLine(decision.reason)
      is PolicyDecision.Apply -> applyLine(decision, network, excludedSsids)
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
