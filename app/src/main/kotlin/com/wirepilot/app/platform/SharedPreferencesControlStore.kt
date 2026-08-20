package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.ControlCodec
import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.StoredControl

class SharedPreferencesControlStore(
  private val preferences: SharedPreferences,
) : ControlStore {
  override fun read(): StoredControl {
    val pausedUntil = preferences.getLong(PreferenceKeys.PAUSED_UNTIL, 0L)
    return StoredControl(
      enabled = preferences.getBoolean(PreferenceKeys.ENABLED, true),
      pausedUntilEpochMillis = pausedUntil.takeIf { it > 0L },
      tunnelName = preferences.getString(PreferenceKeys.TUNNEL_NAME, "").orEmpty(),
      excludedSsids = ControlCodec.decodeSsids(preferences.getString(PreferenceKeys.EXCLUDED_SSIDS, "")),
      connectOnMobile = preferences.getBoolean(PreferenceKeys.CONNECT_ON_MOBILE, true),
    )
  }

  override fun write(control: StoredControl) {
    preferences.edit {
      putBoolean(PreferenceKeys.ENABLED, control.enabled)
      putLong(PreferenceKeys.PAUSED_UNTIL, control.pausedUntilEpochMillis ?: 0L)
      putString(PreferenceKeys.TUNNEL_NAME, control.tunnelName)
      putString(PreferenceKeys.EXCLUDED_SSIDS, ControlCodec.encodeSsids(control.excludedSsids))
      putBoolean(PreferenceKeys.CONNECT_ON_MOBILE, control.connectOnMobile)
    }
  }
}
