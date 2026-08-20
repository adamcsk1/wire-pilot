package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ControlCodecTest {
  @Test
  fun encodeSortsSsids() {
    assertEquals("Cafe\u001EHome", ControlCodec.encodeSsids(setOf("Home", "Cafe")))
  }

  @Test
  fun decodeEmptyAndNull() {
    assertEquals(emptySet(), ControlCodec.decodeSsids(null))
    assertEquals(emptySet(), ControlCodec.decodeSsids(""))
  }

  @Test
  fun roundTrip() {
    val ssids = setOf("Home", "Cafe")
    assertEquals(ssids, ControlCodec.decodeSsids(ControlCodec.encodeSsids(ssids)))
  }

  @Test
  fun decodeDropsUnknownEntries() {
    assertEquals(setOf("Home"), ControlCodec.decodeSsids("Home\u001E<unknown ssid>"))
  }
}
