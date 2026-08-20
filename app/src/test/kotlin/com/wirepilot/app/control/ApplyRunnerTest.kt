package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.RecordingLog
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplyRunnerTest {
  @Test
  fun sendsUpWhenPolicyMatches() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    )

    runner.applyNow()

    assertEquals(listOf("office" to TunnelCommand.UP), tunnel.commands)
  }

  @Test
  fun sendsDownOnExcludedSsid() {
    val store = InMemoryControlStore(
      StoredControl(tunnelName = "office", excludedSsids = setOf("Home")),
    )
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI, setOf("Home")) },
      tunnel = tunnel,
    )

    runner.applyNow()

    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun skipDoesNotSend() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    )

    runner.applyNow()

    assertTrue(tunnel.commands.isEmpty())
  }

  @Test
  fun persistsExpiredPauseThenApplies() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = 5L, tunnelName = "office"),
    )
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    )

    runner.applyNow()

    assertEquals(true, store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertEquals(listOf("office" to TunnelCommand.UP), tunnel.commands)
  }

  @Test
  fun doesNotRewriteUnchangedStore() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    var writes = 0
    val countingStore = object : com.wirepilot.app.data.ControlStore {
      override fun read() = store.read()
      override fun write(control: com.wirepilot.app.data.StoredControl) {
        writes += 1
        store.write(control)
      }
    }
    ApplyRunner(
      store = countingStore,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = RecordingTunnel(),
    ).applyNow()
    assertEquals(0, writes)
  }

  @Test
  fun logsDebounceKindForDebounceTrigger() {
    val log = RecordingLog()
    ApplyRunner(
      store = InMemoryControlStore(StoredControl(tunnelName = "office")),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = RecordingTunnel(),
      log = log,
    ).applyNow("debounce")
    assertEquals(LogKind.DEBOUNCE, log.entries.single().first)
  }

  @Test
  fun requestsRetryWhenWifiUnreadable() {
    val retry = ApplyRunner(
      store = InMemoryControlStore(StoredControl(tunnelName = "office")),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      tunnel = RecordingTunnel(),
    ).applyNow("debounce")
    assertTrue(retry)
  }

  @Test
  fun doesNotRequestRetryOnRetryTrigger() {
    val retry = ApplyRunner(
      store = InMemoryControlStore(StoredControl(tunnelName = "office")),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      tunnel = RecordingTunnel(),
    ).applyNow("unreadable-retry-5")
    assertFalse(retry)
  }
}
