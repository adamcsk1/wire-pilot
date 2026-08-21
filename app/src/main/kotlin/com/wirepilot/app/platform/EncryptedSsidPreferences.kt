package com.wirepilot.app.platform

import android.content.Context
import android.content.SharedPreferences

object EncryptedSsidPreferences {
  const val FILE = "wire_pilot_ssids"

  fun create(context: Context): SharedPreferences {
    return TinkEncryptedPreferences(context.applicationContext, FILE)
  }
}
