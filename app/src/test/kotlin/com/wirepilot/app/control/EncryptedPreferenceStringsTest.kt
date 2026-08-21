package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncryptedPreferenceStringsTest {
  @Test
  fun encodeUsesAndroidxStringHeader() {
    val encoded = EncryptedPreferenceStrings.encode("office")
    assertEquals(0, encoded[3].toInt())
    assertEquals(6, encoded[7].toInt())
    assertEquals("office", EncryptedPreferenceStrings.decode(encoded))
    assertEquals("", EncryptedPreferenceStrings.decode(EncryptedPreferenceStrings.encode("")))
  }

  @Test
  fun rejectsEmptyAndNonStringPayloads() {
    assertNull(EncryptedPreferenceStrings.decode(byteArrayOf()))
    assertNull(EncryptedPreferenceStrings.decode(byteArrayOf(1)))
    assertNull(EncryptedPreferenceStrings.decode(ByteArray(8) { index -> if (index == 3) 2 else 0 }))
    assertNull(EncryptedPreferenceStrings.decode(ByteArray(8) { index -> if (index == 4) 0xFF.toByte() else 0 }))
    assertNull(EncryptedPreferenceStrings.decode(ByteArray(9)))
  }
}
