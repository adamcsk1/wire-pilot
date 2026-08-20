package com.wirepilot.app.control

data class SsidReadiness(
  val nearbyWifiGranted: Boolean,
  val fineLocationGranted: Boolean,
  val locationEnabled: Boolean,
)

enum class SsidBlocker {
  NEARBY_WIFI_PERMISSION,
  FINE_LOCATION_PERMISSION,
  LOCATION_OFF,
  UNKNOWN_NETWORK,
}

object SsidReadinessEvaluator {
  fun blocker(readiness: SsidReadiness): SsidBlocker? {
    if (!readiness.nearbyWifiGranted) {
      return SsidBlocker.NEARBY_WIFI_PERMISSION
    }
    if (!readiness.fineLocationGranted) {
      return SsidBlocker.FINE_LOCATION_PERMISSION
    }
    if (!readiness.locationEnabled) {
      return SsidBlocker.LOCATION_OFF
    }
    return null
  }

  fun blockerWhenUnreadable(readiness: SsidReadiness): SsidBlocker {
    return blocker(readiness) ?: SsidBlocker.UNKNOWN_NETWORK
  }
}
