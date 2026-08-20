package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import kotlin.test.Test
import kotlin.test.assertEquals

class PauseCalculatorTest {
  private val base = StoredControl(enabled = true, tunnelName = "office", pausedUntilEpochMillis = 9L)

  @Test
  fun alwaysDisablesWithoutDeadline() {
    val next = PauseCalculator.apply(base, PauseOption.ALWAYS, nowMillis = 10L)
    assertEquals(base.copy(enabled = false, pausedUntilEpochMillis = null), next)
  }

  @Test
  fun timedPauseSetsDeadline() {
    val next = PauseCalculator.apply(base, PauseOption.HOURS_2, nowMillis = 1_000L)
    assertEquals(
      base.copy(enabled = false, pausedUntilEpochMillis = 1_000L + 2L * 60L * 60L * 1000L),
      next,
    )
  }

  @Test
  fun resumeClearsPause() {
    val paused = StoredControl(enabled = false, pausedUntilEpochMillis = 50L, tunnelName = "office")
    assertEquals(
      StoredControl(enabled = true, pausedUntilEpochMillis = null, tunnelName = "office"),
      PauseCalculator.resume(paused),
    )
  }

  @Test
  fun allTimedOptionsHaveDurations() {
    val timed = PauseOption.entries.filter { it != PauseOption.ALWAYS }
    assertEquals(listOf(1, 2, 4, 8, 12, 24).map { it * 60L * 60L * 1000L }, timed.map { it.durationMillis })
  }
}
