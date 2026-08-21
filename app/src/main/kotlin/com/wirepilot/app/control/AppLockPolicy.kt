package com.wirepilot.app.control

import com.wirepilot.app.data.AppLockState
import java.security.MessageDigest

object AppLockPolicy {
  const val MIN_PIN_LENGTH = 4
  const val MAX_PIN_LENGTH = 8
  const val BACKGROUND_LOCK_DELAY_MS = 30_000L

  fun shouldLockAfterBackground(elapsedMillis: Long): Boolean {
    return elapsedMillis >= BACKGROUND_LOCK_DELAY_MS
  }

  fun isValidPin(pin: String): Boolean {
    return pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { character -> character.isDigit() }
  }

  fun saltHex(bytes: ByteArray): String {
    if (bytes.isEmpty()) {
      return ""
    }
    return toHex(bytes)
  }

  fun enable(pin: String, confirmPin: String, saltHex: String): AppLockState? {
    if (!isValidPin(pin) || pin != confirmPin || saltHex.isEmpty()) {
      return null
    }
    return AppLockState(
      enabled = true,
      pinSalt = saltHex,
      pinHash = hashPin(pin, saltHex),
      biometricEnabled = false,
    )
  }

  fun verify(state: AppLockState, pin: String): Boolean {
    if (!state.enabled || state.pinSalt.isEmpty() || state.pinHash.isEmpty()) {
      return false
    }
    val expected = fromHex(state.pinHash) ?: return false
    val actual = fromHex(hashPin(pin, state.pinSalt)) ?: return false
    return MessageDigest.isEqual(expected, actual)
  }

  fun disable(state: AppLockState, pin: String): AppLockState? {
    if (!verify(state, pin)) {
      return null
    }
    return AppLockState()
  }

  fun setBiometric(state: AppLockState, enabled: Boolean): AppLockState {
    if (!state.enabled) {
      return state
    }
    return state.copy(biometricEnabled = enabled)
  }

  private fun hashPin(pin: String, saltHex: String): String {
    val salt = fromHex(saltHex) ?: return ""
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(pin.toByteArray(Charsets.UTF_8))
    return toHex(digest.digest())
  }

  private fun toHex(bytes: ByteArray): String {
    return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
  }

  private fun fromHex(hex: String): ByteArray? {
    if (hex.isEmpty() || hex.length % 2 != 0) {
      return null
    }
    return runCatching {
      ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
      }
    }.getOrNull()
  }
}
