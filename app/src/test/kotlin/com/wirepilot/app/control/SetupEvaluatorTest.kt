package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class SetupEvaluatorTest {
  @Test
  fun completeSetupIsEmpty() {
    assertEquals(
      emptyList(),
      SetupEvaluator.steps(
        SetupFlags(
          nearbyWifiGranted = true,
          fineLocationGranted = true,
          locationEnabled = true,
          notificationsGranted = true,
          tunnelImported = true,
          vpnPrepared = true,
        ),
      ),
    )
  }

  @Test
  fun listsMissingStepsInOrder() {
    assertEquals(
      listOf(
        SetupStep.GRANT_NEARBY_WIFI,
        SetupStep.GRANT_FINE_LOCATION,
        SetupStep.ENABLE_LOCATION,
        SetupStep.GRANT_NOTIFICATIONS,
        SetupStep.IMPORT_TUNNEL,
        SetupStep.PREPARE_VPN,
      ),
      SetupEvaluator.steps(
        SetupFlags(
          nearbyWifiGranted = false,
          fineLocationGranted = false,
          locationEnabled = false,
          notificationsGranted = false,
          tunnelImported = false,
          vpnPrepared = false,
        ),
      ),
    )
  }
}
