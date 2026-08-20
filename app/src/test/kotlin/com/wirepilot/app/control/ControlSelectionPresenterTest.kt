package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class ControlSelectionPresenterTest {
  @Test
  fun watchingIsOn() {
    assertEquals(ControlSelection.ON, ControlSelectionPresenter.present(StatusPresentation.Watching))
  }

  @Test
  fun pausedIsPause() {
    assertEquals(ControlSelection.PAUSE, ControlSelectionPresenter.present(StatusPresentation.Paused(10L)))
  }

  @Test
  fun disabledIsOff() {
    assertEquals(ControlSelection.OFF, ControlSelectionPresenter.present(StatusPresentation.Disabled))
  }
}
