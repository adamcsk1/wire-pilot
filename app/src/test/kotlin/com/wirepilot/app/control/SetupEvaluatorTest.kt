package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class SetupEvaluatorTest {
  @Test
  fun alwaysIncludesRemoteControlStep() {
    val steps = SetupEvaluator.steps(
      SetupFlags(
        wireGuardInstalled = true,
        controlPermissionGranted = true,
        nearbyWifiGranted = true,
        fineLocationGranted = true,
        locationEnabled = true,
        notificationsGranted = true,
        tunnelNameSet = true,
      ),
    )
    assertEquals(listOf(SetupStep.ENABLE_REMOTE_CONTROL), steps)
  }

  @Test
  fun listsMissingStepsInOrder() {
    val steps = SetupEvaluator.steps(
      SetupFlags(
        wireGuardInstalled = false,
        controlPermissionGranted = false,
        nearbyWifiGranted = false,
        fineLocationGranted = false,
        locationEnabled = false,
        notificationsGranted = false,
        tunnelNameSet = false,
      ),
    )
    assertEquals(
      listOf(
        SetupStep.INSTALL_WIREGUARD,
        SetupStep.GRANT_CONTROL,
        SetupStep.ENABLE_REMOTE_CONTROL,
        SetupStep.GRANT_NEARBY_WIFI,
        SetupStep.GRANT_FINE_LOCATION,
        SetupStep.ENABLE_LOCATION,
        SetupStep.GRANT_NOTIFICATIONS,
        SetupStep.SET_TUNNEL_NAME,
      ),
      steps,
    )
  }
}
