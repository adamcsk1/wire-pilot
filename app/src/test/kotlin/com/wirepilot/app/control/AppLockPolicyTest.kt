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
    assertNull(AppLockPolicy.disable(state, "0000"))
    assertEquals(AppLockState(), AppLockPolicy.disable(state, "1357"))
  }

  @Test
  fun biometricOnlyWhenEnabled() {
    val disabled = AppLockPolicy.setBiometric(AppLockState(), true)
    assertFalse(disabled.biometricEnabled)
    val enabled = AppLockPolicy.enable("1234", "1234", salt)!!
    assertTrue(AppLockPolicy.setBiometric(enabled, true).biometricEnabled)
    assertFalse(AppLockPolicy.setBiometric(enabled, false).biometricEnabled)
  }
}
