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
    )
    assertEquals(state, AppLockCodec.decode(AppLockCodec.encode(state)))
  }

  @Test
  fun encodeDisabled() {
    assertEquals("0\t\t\t0", AppLockCodec.encode(AppLockState()))
  }

  @Test
  fun decodeMissingBiometricIsOff() {
    val state = AppLockCodec.decode("1\tsalt\thash")
    assertTrue(state.enabled)
    assertEquals("salt", state.pinSalt)
    assertEquals("hash", state.pinHash)
    assertFalse(state.biometricEnabled)
  }
}
