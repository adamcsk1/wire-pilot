package com.wirepilot.app.control

import com.wirepilot.app.data.AppLockState
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockPolicy {
  const val MIN_PIN_LENGTH = 4
  const val MAX_PIN_LENGTH = 8
  const val BACKGROUND_LOCK_DELAY_MS = 30_000L
  const val LOCKOUT_AFTER_FAILURES = 5
  const val LOCKOUT_INITIAL_MS = 30_000L
  const val LOCKOUT_MAX_MS = 15 * 60 * 1000L
  const val PBKDF2_ITERATIONS = 100_000
  private const val PBKDF2_PREFIX = "pbkdf2:"

  data class PinCheck(
    val accepted: Boolean,
    val state: AppLockState,
  )

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
      pinHash = hashPinPbkdf2(pin, saltHex, PBKDF2_ITERATIONS) ?: return null,
      biometricEnabled = false,
    )
  }

  fun verify(state: AppLockState, pin: String): Boolean {
    return pinMatches(state, pin)
  }

  fun isLockedOut(state: AppLockState, nowMillis: Long): Boolean {
    return remainingLockMillis(state, nowMillis) > 0L
  }

  fun remainingLockMillis(state: AppLockState, nowMillis: Long): Long {
    val duration = lockoutMillisAfter(state.failedAttempts)
    if (duration == 0L || state.lockoutStartedMillis == 0L) {
      return 0L
    }
    val elapsed = if (nowMillis < state.lockoutStartedMillis) {
      0L
    } else {
      nowMillis - state.lockoutStartedMillis
    }
    return (duration - elapsed).coerceAtLeast(0L)
  }

  fun lockoutMillisAfter(failedAttempts: Int): Long {
    if (failedAttempts < LOCKOUT_AFTER_FAILURES) {
      return 0L
    }
    val extra = failedAttempts - LOCKOUT_AFTER_FAILURES
    var delay = LOCKOUT_INITIAL_MS
    repeat(extra) {
      delay = (delay * 2).coerceAtMost(LOCKOUT_MAX_MS)
    }
    return delay
  }

  fun checkPin(state: AppLockState, pin: String, nowMillis: Long): PinCheck {
    if (!state.enabled || state.pinSalt.isEmpty() || state.pinHash.isEmpty()) {
      return PinCheck(accepted = false, state = state)
    }
    if (isLockedOut(state, nowMillis)) {
      return PinCheck(accepted = false, state = state)
    }
    if (pinMatches(state, pin)) {
      val migrated = migrateHashIfNeeded(state, pin)
      return PinCheck(
        accepted = true,
        state = migrated.copy(failedAttempts = 0, lockoutStartedMillis = 0L),
      )
    }
    val attempts = state.failedAttempts + 1
    val lockMs = lockoutMillisAfter(attempts)
    return PinCheck(
      accepted = false,
      state = state.copy(
        failedAttempts = attempts,
        lockoutStartedMillis = if (lockMs > 0L) nowMillis else 0L,
      ),
    )
  }

  fun disable(state: AppLockState, pin: String, nowMillis: Long): AppLockState? {
    val checked = checkPin(state, pin, nowMillis)
    if (!checked.accepted) {
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

  private fun migrateHashIfNeeded(state: AppLockState, pin: String): AppLockState {
    if (state.pinHash.startsWith(PBKDF2_PREFIX)) {
      return state
    }
    val upgraded = hashPinPbkdf2(pin, state.pinSalt, PBKDF2_ITERATIONS) ?: return state
    return state.copy(pinHash = upgraded)
  }

  private fun pinMatches(state: AppLockState, pin: String): Boolean {
    val stored = parseStoredHash(state.pinHash) ?: return false
    val computed = when (stored.scheme) {
      HashScheme.PBKDF2 -> hashPinPbkdf2Bytes(pin, state.pinSalt, stored.iterations)
      HashScheme.SHA256 -> hashPinSha256Bytes(pin, state.pinSalt)
    } ?: return false
    return MessageDigest.isEqual(stored.hash, computed)
  }

  private fun parseStoredHash(raw: String): StoredHash? {
    if (raw.startsWith(PBKDF2_PREFIX)) {
      val parts = raw.split(':')
      if (parts.size != 3) {
        return null
      }
      val iterations = parts[1].toIntOrNull() ?: return null
      val hash = fromHex(parts[2]) ?: return null
      return StoredHash(HashScheme.PBKDF2, iterations, hash)
    }
    val hash = fromHex(raw) ?: return null
    return StoredHash(HashScheme.SHA256, iterations = 0, hash = hash)
  }

  private fun hashPinPbkdf2(pin: String, saltHex: String, iterations: Int): String? {
    val bytes = hashPinPbkdf2Bytes(pin, saltHex, iterations) ?: return null
    return "$PBKDF2_PREFIX$iterations:${toHex(bytes)}"
  }

  private fun hashPinPbkdf2Bytes(pin: String, saltHex: String, iterations: Int): ByteArray? {
    if (iterations <= 0) {
      return null
    }
    val salt = fromHex(saltHex) ?: return null
    return runCatching {
      val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
      try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      } finally {
        spec.clearPassword()
      }
    }.getOrNull()
  }

  private fun hashPinSha256Bytes(pin: String, saltHex: String): ByteArray? {
    val salt = fromHex(saltHex) ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(pin.toByteArray(Charsets.UTF_8))
    return digest.digest()
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

  private enum class HashScheme {
    SHA256,
    PBKDF2,
  }

  private data class StoredHash(
    val scheme: HashScheme,
    val iterations: Int,
    val hash: ByteArray,
  )
}
