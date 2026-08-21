package com.wirepilot.app.platform

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

class EncryptedSsidHmacStore(
  context: Context,
) {
  private val preferences = TinkEncryptedPreferences(context.applicationContext, FILE)

  fun getOrCreate(): ByteArray {
    val existing = fromHex(preferences.getString(KEY, null))
    if (existing != null && existing.size == KEY_BYTES) {
      return existing
    }
    val generated = ByteArray(KEY_BYTES)
    SecureRandom().nextBytes(generated)
    preferences.edit {
      putString(KEY, toHex(generated))
    }
    return generated
  }

  private fun toHex(bytes: ByteArray): String {
    return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
  }

  private fun fromHex(hex: String?): ByteArray? {
    if (hex.isNullOrEmpty() || hex.length % 2 != 0) {
      return null
    }
    return runCatching {
      ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
      }
    }.getOrNull()
  }

  companion object {
    private const val FILE = "wire_pilot_hmac"
    private const val KEY = "ssid_hmac"
    const val KEY_BYTES = 32
  }
}
