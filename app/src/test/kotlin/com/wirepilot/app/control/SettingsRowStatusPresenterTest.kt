package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRowStatusPresenterTest {
  @Test
  fun flagMapsOnAndOff() {
    assertEquals(SettingsRowStatus.ON, SettingsRowStatusPresenter.fromFlag(true))
    assertEquals(SettingsRowStatus.OFF, SettingsRowStatusPresenter.fromFlag(false))
  }

  @Test
  fun optionalMapsNullToUnknown() {
    assertEquals(SettingsRowStatus.ON, SettingsRowStatusPresenter.fromOptional(true))
    assertEquals(SettingsRowStatus.OFF, SettingsRowStatusPresenter.fromOptional(false))
    assertEquals(SettingsRowStatus.UNKNOWN, SettingsRowStatusPresenter.fromOptional(null))
  }
}
