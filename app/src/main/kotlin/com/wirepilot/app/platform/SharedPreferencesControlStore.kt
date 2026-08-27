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
    val tunnelName = preferences.getString(PreferenceKeys.TUNNEL_NAME, "").orEmpty()
    val mobileTunnelName = readMobileTunnelName(preferences, tunnelName)
    return StoredControl(
      enabled = preferences.getBoolean(PreferenceKeys.ENABLED, false),
      pausedUntilEpochMillis = pausedUntil.takeIf { it > 0L },
      tunnelName = tunnelName,
      excludedSsids = ControlCodec.decodeSsids(preferences.getString(PreferenceKeys.EXCLUDED_SSIDS, "")),
      mobileTunnelName = mobileTunnelName,
    )
  }

  override fun write(control: StoredControl) {
    preferences.edit {
      putBoolean(PreferenceKeys.ENABLED, control.enabled)
      putLong(PreferenceKeys.PAUSED_UNTIL, control.pausedUntilEpochMillis ?: 0L)
      putString(PreferenceKeys.TUNNEL_NAME, control.tunnelName)
      putString(PreferenceKeys.EXCLUDED_SSIDS, ControlCodec.encodeSsids(control.excludedSsids))
      putBoolean(PreferenceKeys.CONNECT_ON_MOBILE, control.mobileTunnelName.isNotBlank())
      putString(PreferenceKeys.MOBILE_TUNNEL_NAME, control.mobileTunnelName)
    }
  }

  private fun readMobileTunnelName(preferences: SharedPreferences, tunnelName: String): String {
    if (preferences.contains(PreferenceKeys.MOBILE_TUNNEL_NAME)) {
      return preferences.getString(PreferenceKeys.MOBILE_TUNNEL_NAME, "").orEmpty()
    }
    return if (preferences.getBoolean(PreferenceKeys.CONNECT_ON_MOBILE, true)) {
      tunnelName
    } else {
      ""
    }
  }
}
