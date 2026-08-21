package com.wirepilot.app.platform

import androidx.appcompat.app.AppCompatDelegate
import com.wirepilot.app.data.ThemeMode

object AppCompatThemeMode {
  fun apply(mode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(
      when (mode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
      },
    )
  }
}
