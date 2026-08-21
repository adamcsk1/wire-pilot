package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.ControlCodec
import com.wirepilot.app.data.ExcludedSsidStore

class SharedPreferencesExcludedSsidStore(
  private val preferences: SharedPreferences,
) : ExcludedSsidStore {
  override fun read(tunnelName: String): Set<String> {
    return ControlCodec.decodeSsids(preferences.getString(key(tunnelName), null))
  }

  override fun write(tunnelName: String, ssids: Set<String>) {
    preferences.edit {
      putString(key(tunnelName), ControlCodec.encodeSsids(ssids))
    }
  }

  override fun delete(tunnelName: String) {
    preferences.edit {
      remove(key(tunnelName))
    }
  }

  override fun exists(tunnelName: String): Boolean {
    return preferences.contains(key(tunnelName))
  }

  private fun key(tunnelName: String): String {
    return "${PreferenceKeys.EXCLUDED_SSID_PREFIX}$tunnelName"
  }
}
