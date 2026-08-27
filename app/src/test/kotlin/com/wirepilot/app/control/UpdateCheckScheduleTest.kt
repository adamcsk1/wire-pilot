package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckScheduleTest {
  @Test
  fun intervalIsOneDay() {
    assertEquals(24L * 60L * 60L * 1000L, UpdateCheckSchedule.INTERVAL_MS)
  }

  @Test
  fun firstCheckIsDueAndSchedulesADayOut() {
    assertTrue(UpdateCheckSchedule.isDue(0L, 10L))
    assertEquals(10L + UpdateCheckSchedule.INTERVAL_MS, UpdateCheckSchedule.nextAt(0L, 10L))
  }

  @Test
  fun notDueBeforeIntervalElapses() {
    val lastCheck = 1_000L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS - 1L
    assertFalse(UpdateCheckSchedule.isDue(lastCheck, now))
    assertEquals(lastCheck + UpdateCheckSchedule.INTERVAL_MS, UpdateCheckSchedule.nextAt(lastCheck, now))
  }

  @Test
  fun dueAfterIntervalElapses() {
    val lastCheck = 1_000L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS
    assertTrue(UpdateCheckSchedule.isDue(lastCheck, now))
    assertEquals(now, UpdateCheckSchedule.nextAt(lastCheck, now))
  }
}
