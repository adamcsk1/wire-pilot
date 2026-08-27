package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlResolverTest {
  @Test
  fun persistResolvedWritesExpiredPause() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = 5L, tunnelName = "office"),
    )
    val resolved = ControlResolver(store) { 10L }.persistResolved()
    assertEquals(true, resolved.enabled)
    assertNull(resolved.pausedUntilEpochMillis)
    assertEquals(true, store.read().enabled)
  }

  @Test
  fun persistResolvedLeavesUnchangedStore() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    var writes = 0
    val counting = object : com.wirepilot.app.data.ControlStore {
      override fun read() = store.read()
      override fun write(control: StoredControl) {
        writes += 1
        store.write(control)
      }
    }
    ControlResolver(counting) { 10L }.persistResolved()
    assertEquals(0, writes)
  }
}
