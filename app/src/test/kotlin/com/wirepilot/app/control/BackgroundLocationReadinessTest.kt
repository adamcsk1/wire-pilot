package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundLocationReadinessTest {
  @Test
  fun preciseGrantMustPrecedeBackgroundSettingsIncludingApproximateOnlyAndRevocation() {
    assertEquals(LocationPermissionStep.PRECISE_LOCATION, BackgroundLocationReadiness.permissionStep(false, false))
    assertEquals(LocationPermissionStep.PRECISE_LOCATION, BackgroundLocationReadiness.permissionStep(false, true))
    assertEquals(LocationPermissionStep.BACKGROUND_LOCATION, BackgroundLocationReadiness.permissionStep(true, false))
    assertEquals(LocationPermissionStep.COMPLETE, BackgroundLocationReadiness.permissionStep(true, true))
    assertEquals(LocationPermissionStep.BACKGROUND_LOCATION, BackgroundLocationReadiness.permissionStep(true, false))
  }

  @Test
  fun backgroundCapabilityIsSeparateFromCurrentSsidReadabilityAndRequiresSuccessfulPromotion() {
    val ready = SsidReadiness(true, true, true)
    assertNull(SsidReadinessEvaluator.blocker(ready))
    assertTrue(BackgroundLocationReadiness.monitoringLimited(ready, false, true))
    assertTrue(BackgroundLocationReadiness.monitoringLimited(ready, true, false))
    assertFalse(BackgroundLocationReadiness.monitoringLimited(ready, true, true))
    assertTrue(BackgroundLocationReadiness.monitoringLimited(ready.copy(nearbyWifiGranted = false), true, true))
    assertTrue(BackgroundLocationReadiness.monitoringLimited(ready.copy(fineLocationGranted = false), true, true))
    assertTrue(BackgroundLocationReadiness.monitoringLimited(ready.copy(locationEnabled = false), true, true))
  }
}
