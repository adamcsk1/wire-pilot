package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.ThemeMode
import com.wirepilot.app.data.ThemeModeCodec
import com.wirepilot.app.data.ThemeModeStore

class SharedPreferencesThemeModeStore(
  private val preferences: SharedPreferences,
) : ThemeModeStore {
  override fun read(): ThemeMode {
    return ThemeModeCodec.decode(preferences.getString(PreferenceKeys.THEME_MODE, null))
  }

  override fun write(mode: ThemeMode) {
    preferences.edit {
      putString(PreferenceKeys.THEME_MODE, ThemeModeCodec.encode(mode))
    }
  }
}
