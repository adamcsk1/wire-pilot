package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class DebounceTriggersTest {
  @Test
  fun extraAndTriggerNames() {
    assertEquals("trigger", DebounceTriggers.EXTRA_TRIGGER)
    assertEquals("debounce", DebounceTriggers.DEBOUNCE)
    assertEquals("process-start", DebounceTriggers.PROCESS_START)
  }
}
