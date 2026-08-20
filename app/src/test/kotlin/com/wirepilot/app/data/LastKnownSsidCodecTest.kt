package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastKnownSsidCodecTest {
  @Test
  fun roundTrip() {
    val stored = StoredLastKnownSsid(ssid = "Home", atMillis = 42L)
    assertEquals(stored, LastKnownSsidCodec.decode(LastKnownSsidCodec.encode(stored)))
  }

  @Test
  fun blankIsNull() {
    assertEquals("", LastKnownSsidCodec.encode(null))
    assertEquals("", LastKnownSsidCodec.encode(StoredLastKnownSsid(ssid = "  ")))
    assertNull(LastKnownSsidCodec.decode(null))
    assertNull(LastKnownSsidCodec.decode(""))
    assertNull(LastKnownSsidCodec.decode("   "))
  }

  @Test
  fun ssidOnlyStillDecodes() {
    assertEquals(StoredLastKnownSsid(ssid = "Cafe"), LastKnownSsidCodec.decode("Cafe"))
  }
}
