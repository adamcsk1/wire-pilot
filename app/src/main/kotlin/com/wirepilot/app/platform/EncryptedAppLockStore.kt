package com.wirepilot.app.platform

import android.content.Context
import androidx.core.content.edit
import com.wirepilot.app.data.AppLockCodec
import com.wirepilot.app.data.AppLockState
import com.wirepilot.app.data.AppLockStore

class EncryptedAppLockStore(
  context: Context,
) : AppLockStore {
  private val preferences = TinkEncryptedPreferences(context.applicationContext, FILE)

  override fun read(): AppLockState {
    return AppLockCodec.decode(preferences.getString(KEY, null))
  }

  override fun write(state: AppLockState) {
    preferences.edit {
      putString(KEY, AppLockCodec.encode(state))
    }
  }

  companion object {
    private const val FILE = "wire_pilot_lock"
    private const val KEY = "app_lock"
  }
}
