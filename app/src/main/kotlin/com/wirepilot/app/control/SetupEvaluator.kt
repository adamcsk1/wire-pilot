package com.wirepilot.app.control

data class SetupFlags(
  val nearbyWifiGranted: Boolean,
  val fineLocationGranted: Boolean,
  val locationEnabled: Boolean,
  val notificationsGranted: Boolean,
  val tunnelImported: Boolean,
  val vpnPrepared: Boolean,
)

enum class SetupStep {
  GRANT_NEARBY_WIFI,
  GRANT_FINE_LOCATION,
  ENABLE_LOCATION,
  GRANT_NOTIFICATIONS,
  IMPORT_TUNNEL,
  PREPARE_VPN,
}

object SetupEvaluator {
  fun steps(flags: SetupFlags): List<SetupStep> {
    val steps = mutableListOf<SetupStep>()
    if (!flags.nearbyWifiGranted) {
      steps += SetupStep.GRANT_NEARBY_WIFI
    }
    if (!flags.fineLocationGranted) {
      steps += SetupStep.GRANT_FINE_LOCATION
    }
    if (!flags.locationEnabled) {
      steps += SetupStep.ENABLE_LOCATION
    }
    if (!flags.notificationsGranted) {
      steps += SetupStep.GRANT_NOTIFICATIONS
    }
    if (!flags.tunnelImported) {
      steps += SetupStep.IMPORT_TUNNEL
    }
    if (!flags.vpnPrepared) {
      steps += SetupStep.PREPARE_VPN
    }
    return steps
  }
}
