package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockCodecTest {
  @Test
  fun defaultWhenNullOrEmpty() {
    assertEquals(AppLockState(), AppLockCodec.decode(null))
    assertEquals(AppLockState(), AppLockCodec.decode(""))
  }

  @Test
  fun defaultWhenTruncated() {
    assertEquals(AppLockState(), AppLockCodec.decode("1\tabc"))
  }

  @Test
  fun roundTrip() {
    val state = AppLockState(
      enabled = true,
      pinSalt = "abcd",
      pinHash = "ef01",
      biometricEnabled = true,
      failedAttempts = 3,
      lockoutStartedMillis = 99L,
    )
    assertEquals(state, AppLockCodec.decode(AppLockCodec.encode(state)))
  }

  @Test
  fun encodeDisabled() {
    assertEquals("0\t\t\t0\t0\t0", AppLockCodec.encode(AppLockState()))
  }

  @Test
  fun decodeMissingBiometricIsOff() {
    val state = AppLockCodec.decode("1\tsalt\thash")
    assertTrue(state.enabled)
    assertEquals("salt", state.pinSalt)
    assertEquals("hash", state.pinHash)
    assertFalse(state.biometricEnabled)
    assertEquals(0, state.failedAttempts)
    assertEquals(0L, state.lockoutStartedMillis)
  }

  @Test
  fun decodeLegacyFourFields() {
    val state = AppLockCodec.decode("1\tsalt\thash\t1")
    assertTrue(state.biometricEnabled)
    assertEquals(0, state.failedAttempts)
    assertEquals(0L, state.lockoutStartedMillis)
  }

  @Test
  fun decodeIgnoresNegativeCounts() {
    val state = AppLockCodec.decode("1\tsalt\thash\t0\t-2\t-9")
    assertEquals(0, state.failedAttempts)
    assertEquals(0L, state.lockoutStartedMillis)
  }
}
