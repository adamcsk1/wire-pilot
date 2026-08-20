package com.wirepilot.app.control

data class SetupFlags(
  val wireGuardInstalled: Boolean,
  val controlPermissionGranted: Boolean,
  val nearbyWifiGranted: Boolean,
  val fineLocationGranted: Boolean,
  val locationEnabled: Boolean,
  val notificationsGranted: Boolean,
  val tunnelNameSet: Boolean,
)

enum class SetupStep {
  INSTALL_WIREGUARD,
  GRANT_CONTROL,
  ENABLE_REMOTE_CONTROL,
  GRANT_NEARBY_WIFI,
  GRANT_FINE_LOCATION,
  ENABLE_LOCATION,
  GRANT_NOTIFICATIONS,
  SET_TUNNEL_NAME,
}

object SetupEvaluator {
  fun steps(flags: SetupFlags): List<SetupStep> {
    val steps = mutableListOf<SetupStep>()
    if (!flags.wireGuardInstalled) {
      steps += SetupStep.INSTALL_WIREGUARD
    }
    if (!flags.controlPermissionGranted) {
      steps += SetupStep.GRANT_CONTROL
    }
    steps += SetupStep.ENABLE_REMOTE_CONTROL
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
    if (!flags.tunnelNameSet) {
      steps += SetupStep.SET_TUNNEL_NAME
    }
    return steps
  }
}
