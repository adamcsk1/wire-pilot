package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebouncePolicyTest {
  @Test
  fun nextFireAddsThreeSeconds() {
    assertEquals(4_000L, DebouncePolicy.nextFireAt(1_000L))
    assertEquals(3_000L, DebouncePolicy.WINDOW_MS)
  }

  @Test
  fun isDueAtExactTime() {
    assertTrue(DebouncePolicy.isDue(scheduledAtMillis = 15_000L, nowMillis = 15_000L))
  }

  @Test
  fun isDueAfterTime() {
    assertTrue(DebouncePolicy.isDue(scheduledAtMillis = 15_000L, nowMillis = 15_001L))
  }

  @Test
  fun isNotDueBeforeTime() {
    assertFalse(DebouncePolicy.isDue(scheduledAtMillis = 15_000L, nowMillis = 14_999L))
  }
}
