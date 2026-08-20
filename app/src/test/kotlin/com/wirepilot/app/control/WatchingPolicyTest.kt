package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchingPolicyTest {
  @Test
  fun watchesOnlyWhenOn() {
    assertTrue(WatchingPolicy.shouldWatch(StatusPresentation.Watching))
    assertFalse(WatchingPolicy.shouldWatch(StatusPresentation.Disabled))
    assertFalse(WatchingPolicy.shouldWatch(StatusPresentation.Paused(10L)))
  }
}

class WatchingSyncPolicyTest {
  @Test
  fun stopWhenNotWatching() {
    assertEquals(WatchingOutcome.STOP, WatchingSyncPolicy.outcome(wantWatch = false, notificationsGranted = true))
    assertEquals(WatchingOutcome.STOP, WatchingSyncPolicy.outcome(wantWatch = false, notificationsGranted = false))
  }

  @Test
  fun noPermissionWhenWatchingWithoutNotifications() {
    assertEquals(
      WatchingOutcome.NO_PERMISSION,
      WatchingSyncPolicy.outcome(wantWatch = true, notificationsGranted = false),
    )
  }

  @Test
  fun startWhenWatchingAndNotificationsGranted() {
    assertEquals(WatchingOutcome.START, WatchingSyncPolicy.outcome(wantWatch = true, notificationsGranted = true))
  }
}
