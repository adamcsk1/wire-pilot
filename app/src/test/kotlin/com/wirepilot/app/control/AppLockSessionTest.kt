package com.wirepilot.app.control

import com.wirepilot.app.support.InMemoryAppLockStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockSessionTest {
  private val salt = AppLockPolicy.saltHex(byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2))

  @Test
  fun disabledNeedsNoChallenge() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertFalse(session.isEnabled())
    assertFalse(session.needsChallenge())
  }

  @Test
  fun enableUnlocksAndDisableRequiresPin() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertTrue(session.enable("1234", "1234", salt))
    assertTrue(session.isEnabled())
    assertFalse(session.needsChallenge())
    session.lock()
    session.unlock()
    assertFalse(session.needsChallenge())
    session.lock()
    assertTrue(session.needsChallenge())
    assertFalse(session.disable("0000"))
    assertTrue(session.needsChallenge())
    assertTrue(session.disable("1234"))
    assertFalse(session.isEnabled())
    assertFalse(session.needsChallenge())
  }

  @Test
  fun verifyPinUnlocks() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertTrue(session.enable("5555", "5555", salt))
    session.lock()
    assertFalse(session.verifyPin("1111"))
    assertTrue(session.needsChallenge())
    assertTrue(session.verifyPin("5555"))
    assertFalse(session.needsChallenge())
  }

  @Test
  fun biometricUnlockRequiresFlag() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertTrue(session.enable("9999", "9999", salt))
    session.lock()
    assertFalse(session.unlockWithBiometric())
    assertTrue(session.needsChallenge())
    assertTrue(session.setBiometric(true))
    assertTrue(session.unlockWithBiometric())
    assertFalse(session.needsChallenge())
  }

  @Test
  fun biometricFlagIgnoredWhenDisabled() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertFalse(session.setBiometric(true))
    assertFalse(session.state().biometricEnabled)
  }

  @Test
  fun enableRejectsBadPin() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertFalse(session.enable("12", "12", salt))
    assertFalse(session.isEnabled())
  }

  @Test
  fun enableDoesNotOverwriteExistingLock() {
    val session = AppLockSession(InMemoryAppLockStore())
    assertTrue(session.enable("1234", "1234", salt))
    session.lock()
    assertFalse(session.enable("9999", "9999", salt))
    assertFalse(session.verifyPin("9999"))
    assertTrue(session.verifyPin("1234"))
  }

  @Test
  fun failedPinsPersistAndLockOut() {
    var now = 1_000L
    val session = AppLockSession(InMemoryAppLockStore(), clock = { now })
    assertTrue(session.enable("1234", "1234", salt))
    session.lock()
    repeat(5) {
      assertFalse(session.verifyPin("0000"))
    }
    assertEquals(AppLockPolicy.LOCKOUT_INITIAL_MS, session.lockoutRemainingMillis())
    assertFalse(session.verifyPin("1234"))
    now += AppLockPolicy.LOCKOUT_INITIAL_MS
    assertTrue(session.verifyPin("1234"))
    assertEquals(0L, session.lockoutRemainingMillis())
    assertFalse(session.needsChallenge())
  }
}
