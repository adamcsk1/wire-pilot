package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class DurationFormatterTest {
  @Test
  fun splitsHoursAndMinutes() {
    assertEquals(2L to 5L, DurationFormatter.remainingHoursAndMinutes(2L * 60L * 60L * 1000L + 5L * 60L * 1000L))
  }

  @Test
  fun clampsNegativeToZero() {
    assertEquals(0L to 0L, DurationFormatter.remainingHoursAndMinutes(-5L))
  }

  @Test
  fun underOneMinuteIsZeroZero() {
    assertEquals(0L to 0L, DurationFormatter.remainingHoursAndMinutes(59_999L))
  }
}
