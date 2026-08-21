package com.wirepilot.app.control

import com.wirepilot.app.data.AppLockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockPolicyTest {
  private val salt = AppLockPolicy.saltHex(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

  @Test
  fun pinMustBeFourToEightDigits() {
    assertFalse(AppLockPolicy.isValidPin("123"))
    assertFalse(AppLockPolicy.isValidPin("123456789"))
    assertFalse(AppLockPolicy.isValidPin("12ab"))
    assertTrue(AppLockPolicy.isValidPin("1234"))
    assertTrue(AppLockPolicy.isValidPin("12345678"))
  }

  @Test
  fun emptySaltIsBlank() {
    assertEquals("", AppLockPolicy.saltHex(byteArrayOf()))
  }

  @Test
  fun enableRejectsMismatchOrBadPin() {
    assertNull(AppLockPolicy.enable("1234", "4321", salt))
    assertNull(AppLockPolicy.enable("12", "12", salt))
    assertNull(AppLockPolicy.enable("1234", "1234", ""))
  }

  @Test
  fun enableAndVerify() {
    val state = AppLockPolicy.enable("2468", "2468", salt)
    assertNotNull(state)
    assertTrue(state.enabled)
    assertFalse(state.biometricEnabled)
    assertTrue(AppLockPolicy.verify(state, "2468"))
    assertFalse(AppLockPolicy.verify(state, "2469"))
    assertFalse(AppLockPolicy.verify(state, "246"))
  }

  @Test
  fun verifyRejectsMalformedPbkdf2() {
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = salt, pinHash = "pbkdf2:x:00"), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = salt, pinHash = "pbkdf2:100000"), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = salt, pinHash = "pbkdf2:0:00"), "1234"))
  }

  @Test
  fun verifyRejectsDisabledOrCorrupt() {
    assertFalse(AppLockPolicy.verify(AppLockState(), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = "", pinHash = "ab"), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = salt, pinHash = ""), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = salt, pinHash = "zz"), "1234"))
    assertFalse(AppLockPolicy.verify(AppLockState(enabled = true, pinSalt = "abc", pinHash = "00"), "1234"))
  }

  @Test
  fun disableRequiresPin() {
    val state = AppLockPolicy.enable("1357", "1357", salt)!!
    assertNull(AppLockPolicy.disable(state, "0000", nowMillis = 0L))
    assertEquals(AppLockState(), AppLockPolicy.disable(state, "1357", nowMillis = 0L))
  }

  @Test
  fun backgroundLockAfterThirtySeconds() {
    assertFalse(AppLockPolicy.shouldLockAfterBackground(0L))
    assertFalse(AppLockPolicy.shouldLockAfterBackground(AppLockPolicy.BACKGROUND_LOCK_DELAY_MS - 1L))
    assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockPolicy.BACKGROUND_LOCK_DELAY_MS))
    assertTrue(AppLockPolicy.shouldLockAfterBackground(AppLockPolicy.BACKGROUND_LOCK_DELAY_MS + 1L))
  }

  @Test
  fun biometricOnlyWhenEnabled() {
    val disabled = AppLockPolicy.setBiometric(AppLockState(), true)
    assertFalse(disabled.biometricEnabled)
    val enabled = AppLockPolicy.enable("1234", "1234", salt)!!
    assertTrue(AppLockPolicy.setBiometric(enabled, true).biometricEnabled)
    assertFalse(AppLockPolicy.setBiometric(enabled, false).biometricEnabled)
  }

  @Test
  fun lockoutStartsAtFiveFailures() {
    assertEquals(0L, AppLockPolicy.lockoutMillisAfter(4))
    assertEquals(AppLockPolicy.LOCKOUT_INITIAL_MS, AppLockPolicy.lockoutMillisAfter(5))
    assertEquals(AppLockPolicy.LOCKOUT_INITIAL_MS * 2, AppLockPolicy.lockoutMillisAfter(6))
    assertEquals(AppLockPolicy.LOCKOUT_MAX_MS, AppLockPolicy.lockoutMillisAfter(20))
  }

  @Test
  fun noLockoutWhenStartMissing() {
    val enabled = AppLockPolicy.enable("2468", "2468", salt)!!
    val state = enabled.copy(failedAttempts = 5, lockoutStartedMillis = 0L)
    assertEquals(0L, AppLockPolicy.remainingLockMillis(state, nowMillis = 1_000L))
    assertFalse(AppLockPolicy.isLockedOut(state, nowMillis = 1_000L))
  }

  @Test
  fun wrongPinAfterExpiryDoublesLockout() {
    var state = AppLockPolicy.enable("2468", "2468", salt)!!
    repeat(5) {
      state = AppLockPolicy.checkPin(state, "0000", nowMillis = 10L).state
    }
    val afterExpiry = AppLockPolicy.checkPin(state, "0000", nowMillis = 10L + AppLockPolicy.LOCKOUT_INITIAL_MS)
    assertFalse(afterExpiry.accepted)
    assertEquals(6, afterExpiry.state.failedAttempts)
    assertEquals(10L + AppLockPolicy.LOCKOUT_INITIAL_MS, afterExpiry.state.lockoutStartedMillis)
    assertEquals(
      AppLockPolicy.LOCKOUT_INITIAL_MS * 2,
      AppLockPolicy.remainingLockMillis(afterExpiry.state, afterExpiry.state.lockoutStartedMillis),
    )
  }

  @Test
  fun lockoutSurvivesElapsedRealtimeReset() {
    val enabled = AppLockPolicy.enable("2468", "2468", salt)!!
    val state = enabled.copy(failedAttempts = 5, lockoutStartedMillis = 10_000_000L)
    assertEquals(AppLockPolicy.LOCKOUT_INITIAL_MS - 1_000L, AppLockPolicy.remainingLockMillis(state, nowMillis = 1_000L))
    assertTrue(AppLockPolicy.isLockedOut(state, nowMillis = 1_000L))
    assertFalse(AppLockPolicy.isLockedOut(state, nowMillis = AppLockPolicy.LOCKOUT_INITIAL_MS))
    val afterBootWindow = AppLockPolicy.checkPin(state, "2468", nowMillis = AppLockPolicy.LOCKOUT_INITIAL_MS)
    assertTrue(afterBootWindow.accepted)
  }

  @Test
  fun checkPinLockoutThenReset() {
    var state = AppLockPolicy.enable("2468", "2468", salt)!!
    repeat(4) { index ->
      val checked = AppLockPolicy.checkPin(state, "0000", nowMillis = index.toLong())
      assertFalse(checked.accepted)
      assertFalse(AppLockPolicy.isLockedOut(checked.state, nowMillis = index.toLong()))
      state = checked.state
    }
    val locked = AppLockPolicy.checkPin(state, "0000", nowMillis = 10L)
    assertFalse(locked.accepted)
    assertTrue(AppLockPolicy.isLockedOut(locked.state, nowMillis = 10L))
    assertEquals(AppLockPolicy.LOCKOUT_INITIAL_MS - 10L, AppLockPolicy.remainingLockMillis(locked.state, 20L))
    val stillLocked = AppLockPolicy.checkPin(locked.state, "2468", nowMillis = 10L)
    assertFalse(stillLocked.accepted)
    assertEquals(locked.state, stillLocked.state)
    val unlocked = AppLockPolicy.checkPin(locked.state, "2468", nowMillis = 10L + AppLockPolicy.LOCKOUT_INITIAL_MS)
    assertTrue(unlocked.accepted)
    assertEquals(0, unlocked.state.failedAttempts)
    assertEquals(0L, unlocked.state.lockoutStartedMillis)
  }

  @Test
  fun checkPinRejectsDisabled() {
    val checked = AppLockPolicy.checkPin(AppLockState(), "1234", nowMillis = 1L)
    assertFalse(checked.accepted)
    assertEquals(AppLockState(), checked.state)
  }

  @Test
  fun legacySha256MigratesOnSuccess() {
    val legacy = AppLockState(
      enabled = true,
      pinSalt = salt,
      pinHash = sha256Hash("2468", salt),
    )
    assertTrue(AppLockPolicy.verify(legacy, "2468"))
    val checked = AppLockPolicy.checkPin(legacy, "2468", nowMillis = 1L)
    assertTrue(checked.accepted)
    assertTrue(checked.state.pinHash.startsWith("pbkdf2:${AppLockPolicy.PBKDF2_ITERATIONS}:"))
    assertTrue(AppLockPolicy.verify(checked.state, "2468"))
  }

  @Test
  fun disableUsesLockoutClock() {
    var state = AppLockPolicy.enable("1357", "1357", salt)!!
    repeat(5) { index ->
      state = AppLockPolicy.checkPin(state, "0000", nowMillis = index.toLong()).state
    }
    assertNull(AppLockPolicy.disable(state, "1357", nowMillis = 0L))
    assertEquals(
      AppLockState(),
      AppLockPolicy.disable(
        state,
        "1357",
        nowMillis = state.lockoutStartedMillis + AppLockPolicy.lockoutMillisAfter(state.failedAttempts),
      ),
    )
  }

  private fun sha256Hash(pin: String, saltHex: String): String {
    val salt = ByteArray(saltHex.length / 2) { index ->
      saltHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(pin.toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
  }
}
