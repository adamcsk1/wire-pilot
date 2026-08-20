package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigZipLimitsTest {
  @Test
  fun acceptsFirstSmallEntry() {
    assertTrue(ConfigZipLimits.acceptEntry(0, 100, 100))
  }

  @Test
  fun rejectsTooManyEntries() {
    assertFalse(ConfigZipLimits.acceptEntry(ConfigZipLimits.MAX_ENTRIES, 100, 100))
  }

  @Test
  fun rejectsEmptyOrOversizedEntry() {
    assertFalse(ConfigZipLimits.acceptEntry(0, 0, 0))
    assertFalse(ConfigZipLimits.acceptEntry(0, ConfigZipLimits.MAX_ENTRY_BYTES + 1, ConfigZipLimits.MAX_ENTRY_BYTES + 1))
  }

  @Test
  fun rejectsOverTotal() {
    assertFalse(ConfigZipLimits.acceptEntry(1, 10, ConfigZipLimits.MAX_TOTAL_BYTES + 1))
  }
}
