package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeCodecTest {
  @Test
  fun roundTripsAllModes() {
    ThemeMode.entries.forEach { mode ->
      assertEquals(mode, ThemeModeCodec.decode(ThemeModeCodec.encode(mode)))
    }
  }

  @Test
  fun defaultsMissingAndInvalidValuesToSystem() {
    assertEquals(ThemeMode.SYSTEM, ThemeModeCodec.decode(null))
    assertEquals(ThemeMode.SYSTEM, ThemeModeCodec.decode("unknown"))
  }

  @Test
  fun decodesStoredValuesCaseInsensitively() {
    assertEquals(ThemeMode.DARK, ThemeModeCodec.decode("DaRk"))
  }
}
