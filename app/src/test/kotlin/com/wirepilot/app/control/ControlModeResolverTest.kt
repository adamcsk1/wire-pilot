package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import kotlin.test.Test
import kotlin.test.assertEquals

class ControlModeResolverTest {
  @Test
  fun enabledClearsStalePause() {
    val resolved = ControlModeResolver.resolve(
      StoredControl(enabled = true, pausedUntilEpochMillis = 50L),
      nowMillis = 10L,
    )
    assertEquals(StoredControl(enabled = true, pausedUntilEpochMillis = null), resolved)
  }

  @Test
  fun enabledWithoutPauseUnchanged() {
    val control = StoredControl(enabled = true, tunnelName = "a")
    assertEquals(control, ControlModeResolver.resolve(control, 1L))
  }

  @Test
  fun disabledWithoutPauseUnchanged() {
    val control = StoredControl(enabled = false)
    assertEquals(control, ControlModeResolver.resolve(control, 1L))
  }

  @Test
  fun pauseStillActiveStaysDisabled() {
    val control = StoredControl(enabled = false, pausedUntilEpochMillis = 100L)
    assertEquals(control, ControlModeResolver.resolve(control, 99L))
  }

  @Test
  fun pauseExpiryEnablesControl() {
    val resolved = ControlModeResolver.resolve(
      StoredControl(enabled = false, pausedUntilEpochMillis = 100L, tunnelName = "office"),
      nowMillis = 100L,
    )
    assertEquals(
      StoredControl(enabled = true, pausedUntilEpochMillis = null, tunnelName = "office"),
      resolved,
    )
  }
}
