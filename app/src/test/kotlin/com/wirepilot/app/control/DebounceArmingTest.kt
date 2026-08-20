package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebounceArmingTest {
  @Test
  fun armsWhenNothingScheduled() {
    assertTrue(DebounceArming.shouldArm(nowMillis = 10L, scheduledAtMillis = null))
  }

  @Test
  fun skipsWhileStillArmed() {
    assertFalse(DebounceArming.shouldArm(nowMillis = 10L, scheduledAtMillis = 20L))
  }

  @Test
  fun armsAgainWhenDue() {
    assertTrue(DebounceArming.shouldArm(nowMillis = 20L, scheduledAtMillis = 20L))
    assertTrue(DebounceArming.shouldArm(nowMillis = 21L, scheduledAtMillis = 20L))
  }
}
