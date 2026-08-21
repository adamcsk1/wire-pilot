package com.wirepilot.app.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedSsidPreferences {
  const val FILE = "wire_pilot_ssids"

  fun create(context: Context): SharedPreferences {
    return EncryptedSharedPreferences.create(
      context.applicationContext,
      FILE,
      MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }
}
