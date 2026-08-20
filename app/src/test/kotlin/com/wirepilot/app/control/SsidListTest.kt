package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class SsidListTest {
  @Test
  fun addInsertsNormalizedSsid() {
    assertEquals(setOf("Home"), SsidList.add(emptySet(), "  \"Home\"  "))
  }

  @Test
  fun addIgnoresBlank() {
    assertEquals(emptySet(), SsidList.add(emptySet(), "   "))
  }

  @Test
  fun addIgnoresUnknownSsid() {
    assertEquals(setOf("Cafe"), SsidList.add(setOf("Cafe"), "<unknown ssid>"))
  }

  @Test
  fun addIsIdempotent() {
    assertEquals(setOf("Home"), SsidList.add(setOf("Home"), "Home"))
  }

  @Test
  fun removeDropsNormalizedSsid() {
    assertEquals(emptySet(), SsidList.remove(setOf("Home"), "\"Home\""))
  }

  @Test
  fun removeIgnoresUnknownRaw() {
    assertEquals(setOf("Home"), SsidList.remove(setOf("Home"), "   "))
  }
}
