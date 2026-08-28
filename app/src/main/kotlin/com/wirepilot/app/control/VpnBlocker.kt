package com.wirepilot.app.control

enum class VpnBlocker {
  CONSENT_MISSING,
  OTHER_VPN,
}

object VpnBlockerPresenter {
  fun present(
    policyKind: PolicyLineKind,
    vpnConnected: Boolean,
    consentGranted: Boolean,
    otherVpnActive: Boolean,
  ): VpnBlocker? {
    if (vpnConnected || !wantsUp(policyKind)) {
      return null
    }
    if (!consentGranted) {
      return VpnBlocker.CONSENT_MISSING
    }
    if (otherVpnActive) {
      return VpnBlocker.OTHER_VPN
    }
    return null
  }

  internal fun wantsUp(policyKind: PolicyLineKind): Boolean {
    return when (policyKind) {
      PolicyLineKind.WIFI_UP,
      PolicyLineKind.WIFI_UP_LAST_KNOWN,
      PolicyLineKind.MOBILE_UP,
      -> true
      PolicyLineKind.CONTROL_OFF,
      PolicyLineKind.CONTROL_OFF_CONNECTED,
      PolicyLineKind.PAUSED,
      PolicyLineKind.PAUSED_CONNECTED,
      PolicyLineKind.NO_TUNNEL,
      PolicyLineKind.WIFI_UNREADABLE,
      PolicyLineKind.WIFI_EXCLUDED_DOWN,
      PolicyLineKind.MOBILE_DOWN,
      -> false
    }
  }
}
