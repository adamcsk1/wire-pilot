package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusPresenterTest {
  @Test
  fun watchingWhenEnabled() {
    assertEquals(
      StatusPresentation.Watching,
      StatusPresenter.present(StoredControl(enabled = true), nowMillis = 10L),
    )
  }

  @Test
  fun disabledWhenOffWithoutDeadline() {
    assertEquals(
      StatusPresentation.Disabled,
      StatusPresenter.present(StoredControl(enabled = false), nowMillis = 10L),
    )
  }

  @Test
  fun pausedShowsRemainingTime() {
    assertEquals(
      StatusPresentation.Paused(40L),
      StatusPresenter.present(
        StoredControl(enabled = false, pausedUntilEpochMillis = 50L),
        nowMillis = 10L,
      ),
    )
  }

  @Test
  fun expiredPausePresentsAsWatching() {
    assertEquals(
      StatusPresentation.Watching,
      StatusPresenter.present(
        StoredControl(enabled = false, pausedUntilEpochMillis = 10L),
        nowMillis = 10L,
      ),
    )
  }
}
