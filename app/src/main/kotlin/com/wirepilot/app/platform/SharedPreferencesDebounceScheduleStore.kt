package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.DebounceScheduleStore

class SharedPreferencesDebounceScheduleStore(
  private val preferences: SharedPreferences,
) : DebounceScheduleStore {
  override fun readScheduledAtMillis(): Long? {
    val value = preferences.getLong(PreferenceKeys.DEBOUNCE_SCHEDULED_AT, 0L)
    return value.takeIf { it > 0L }
  }

  override fun writeScheduledAtMillis(scheduledAtMillis: Long?) {
    preferences.edit {
      putLong(PreferenceKeys.DEBOUNCE_SCHEDULED_AT, scheduledAtMillis ?: 0L)
    }
  }
}
