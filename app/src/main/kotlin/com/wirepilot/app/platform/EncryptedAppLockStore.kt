package com.wirepilot.app.platform

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wirepilot.app.data.AppLockCodec
import com.wirepilot.app.data.AppLockState
import com.wirepilot.app.data.AppLockStore

class EncryptedAppLockStore(
  context: Context,
) : AppLockStore {
  private val preferences = EncryptedSharedPreferences.create(
    context.applicationContext,
    FILE,
    MasterKey.Builder(context.applicationContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )

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
