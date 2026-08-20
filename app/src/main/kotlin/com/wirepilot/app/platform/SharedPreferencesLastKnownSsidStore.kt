package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.LastKnownSsidCodec
import com.wirepilot.app.data.LastKnownSsidStore
import com.wirepilot.app.data.StoredLastKnownSsid

class SharedPreferencesLastKnownSsidStore(
  private val preferences: SharedPreferences,
) : LastKnownSsidStore {
  override fun read(): StoredLastKnownSsid? {
    return LastKnownSsidCodec.decode(preferences.getString(PreferenceKeys.LAST_KNOWN_SSID, null))
  }

  override fun write(value: StoredLastKnownSsid) {
    preferences.edit {
      putString(PreferenceKeys.LAST_KNOWN_SSID, LastKnownSsidCodec.encode(value))
    }
  }
}
