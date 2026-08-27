package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.StoredUpdateCheck
import com.wirepilot.app.data.UpdateCheckCodec
import com.wirepilot.app.data.UpdateCheckStore

class SharedPreferencesUpdateCheckStore(
  private val preferences: SharedPreferences,
) : UpdateCheckStore {
  override fun read(): StoredUpdateCheck {
    return UpdateCheckCodec.decode(preferences.getString(PreferenceKeys.UPDATE_CHECK, null))
  }

  override fun write(value: StoredUpdateCheck) {
    preferences.edit {
      putString(PreferenceKeys.UPDATE_CHECK, UpdateCheckCodec.encode(value))
    }
  }
}
