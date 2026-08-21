package com.wirepilot.app.control

enum class SettingsRowStatus {
  ON,
  OFF,
  UNKNOWN,
}

object SettingsRowStatusPresenter {
  fun fromFlag(granted: Boolean): SettingsRowStatus {
    return if (granted) SettingsRowStatus.ON else SettingsRowStatus.OFF
  }

  fun fromOptional(value: Boolean?): SettingsRowStatus {
    return when (value) {
      true -> SettingsRowStatus.ON
      false -> SettingsRowStatus.OFF
      null -> SettingsRowStatus.UNKNOWN
    }
  }
}
