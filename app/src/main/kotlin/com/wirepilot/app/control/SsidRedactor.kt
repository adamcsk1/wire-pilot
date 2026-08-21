package com.wirepilot.app.control

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SsidRedactor {
  private const val HMAC_ALGORITHM = "HmacSHA256"
  private const val KEY_BYTES = 32

  @Volatile
  private var installedKey: ByteArray = ByteArray(0)

  fun installKey(key: ByteArray) {
    installedKey = key.copyOf()
  }

  fun redact(value: String): String {
    return redact(value, installedKey)
  }

  fun redact(value: String, key: ByteArray): String {
    val secret = if (key.size == KEY_BYTES) key else ByteArray(KEY_BYTES)
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
    val digest = mac.doFinal(value.toByteArray(Charsets.UTF_8))
    val hex = digest.take(6).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    return "h$hex"
  }

  fun redactNullable(value: String?): String {
    return if (value == null) "null" else redact(value)
  }
}
