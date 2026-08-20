package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.DiagnosticCodec
import com.wirepilot.app.data.DiagnosticState
import com.wirepilot.app.data.DiagnosticStore

class SharedPreferencesDiagnosticStore(
  private val preferences: SharedPreferences,
) : DiagnosticStore {
  override fun read(): DiagnosticState {
    return DiagnosticCodec.decode(preferences.getString(PreferenceKeys.DIAGNOSTICS, null))
  }

  override fun write(state: DiagnosticState) {
    preferences.edit {
      putString(PreferenceKeys.DIAGNOSTICS, DiagnosticCodec.encode(state))
    }
  }
}
