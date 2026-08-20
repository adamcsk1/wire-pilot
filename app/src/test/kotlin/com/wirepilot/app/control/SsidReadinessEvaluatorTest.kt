package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SsidReadinessEvaluatorTest {
  @Test
  fun missingNearbyWins() {
    assertEquals(
      SsidBlocker.NEARBY_WIFI_PERMISSION,
      SsidReadinessEvaluator.blocker(
        SsidReadiness(nearbyWifiGranted = false, fineLocationGranted = false, locationEnabled = false),
      ),
    )
  }

  @Test
  fun missingFineLocationAfterNearby() {
    assertEquals(
      SsidBlocker.FINE_LOCATION_PERMISSION,
      SsidReadinessEvaluator.blocker(
        SsidReadiness(nearbyWifiGranted = true, fineLocationGranted = false, locationEnabled = true),
      ),
    )
  }

  @Test
  fun locationOffWhenPermissionsGranted() {
    assertEquals(
      SsidBlocker.LOCATION_OFF,
      SsidReadinessEvaluator.blocker(
        SsidReadiness(nearbyWifiGranted = true, fineLocationGranted = true, locationEnabled = false),
      ),
    )
  }

  @Test
  fun readyWhenAllOk() {
    assertNull(
      SsidReadinessEvaluator.blocker(
        SsidReadiness(nearbyWifiGranted = true, fineLocationGranted = true, locationEnabled = true),
      ),
    )
  }

  @Test
  fun unreadableFallsBackToUnknownNetwork() {
    assertEquals(
      SsidBlocker.UNKNOWN_NETWORK,
      SsidReadinessEvaluator.blockerWhenUnreadable(
        SsidReadiness(nearbyWifiGranted = true, fineLocationGranted = true, locationEnabled = true),
      ),
    )
  }
}
