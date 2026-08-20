package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.RecordingPauseAlarm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PauseReschedulerTest {
  @Test
  fun reschedulesActiveTimedPause() {
    val alarms = RecordingPauseAlarm()
    PauseRescheduler(
      store = InMemoryControlStore(
        StoredControl(enabled = false, pausedUntilEpochMillis = 80L),
      ),
      clock = { 10L },
      pauseAlarms = alarms,
    ).rescheduleIfNeeded().also { scheduled ->
      assertTrue(scheduled)
    }
    assertEquals(80L, alarms.scheduledAt)
  }

  @Test
  fun skipsExpiredPause() {
    val alarms = RecordingPauseAlarm()
    PauseRescheduler(
      store = InMemoryControlStore(
        StoredControl(enabled = false, pausedUntilEpochMillis = 10L),
      ),
      clock = { 10L },
      pauseAlarms = alarms,
    ).rescheduleIfNeeded().also { scheduled ->
      assertFalse(scheduled)
    }
    assertNull(alarms.scheduledAt)
  }

  @Test
  fun skipsAlwaysOff() {
    val alarms = RecordingPauseAlarm()
    PauseRescheduler(
      store = InMemoryControlStore(StoredControl(enabled = false)),
      clock = { 10L },
      pauseAlarms = alarms,
    ).rescheduleIfNeeded()
    assertNull(alarms.scheduledAt)
  }

  @Test
  fun skipsEnabledControl() {
    val alarms = RecordingPauseAlarm()
    PauseRescheduler(
      store = InMemoryControlStore(
        StoredControl(enabled = true, pausedUntilEpochMillis = 80L),
      ),
      clock = { 10L },
      pauseAlarms = alarms,
    ).rescheduleIfNeeded()
    assertNull(alarms.scheduledAt)
  }
}
