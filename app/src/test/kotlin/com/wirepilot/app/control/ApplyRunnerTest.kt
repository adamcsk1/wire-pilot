package com.wirepilot.app.control

import com.wirepilot.app.data.LogKind
import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.RecordingLog
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplyRunnerTest {
  @Test
  fun sendsUpWhenPolicyMatches() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "office"))
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
  fun sendsUpToMobileTunnelWhenDifferent() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val tunnel = RecordingTunnel()
    ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    ).applyNow()
    assertEquals(listOf("travel" to TunnelCommand.UP), tunnel.commands)
  }

  @Test
  fun excludedSsidDownsCompanionMobile() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Home"), mobileTunnelName = "travel"),
    )
    val tunnel = RecordingTunnel()
    ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI, setOf("Home")) },
      tunnel = tunnel,
    ).applyNow()
    assertEquals(
      listOf("office" to TunnelCommand.DOWN, "travel" to TunnelCommand.DOWN),
      tunnel.commands,
    )
  }

  @Test
  fun sendsCompanionDownAsOneBatch() {
    val batches = mutableListOf<List<Pair<String, TunnelCommand>>>()
    val tunnel = object : TunnelCommands {
      override fun send(tunnelName: String, command: TunnelCommand) {
        error("expected batch send")
      }

      override fun send(commands: List<Pair<String, TunnelCommand>>) {
        batches += commands
      }
    }
    ApplyRunner(
      store = InMemoryControlStore(
        StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
      ),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    ).applyNow()

    assertEquals(
      listOf(listOf("office" to TunnelCommand.DOWN, "travel" to TunnelCommand.DOWN)),
      batches,
    )
  }

  @Test
  fun sendsDownOnExcludedSsid() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Home")),
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
  fun overlayExcludedSsidsWinOverStored() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Cafe")),
    )
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI, setOf("Home")) },
      tunnel = tunnel,
      excludedSsidsFor = { setOf("Home") },
    )

    runner.applyNow()

    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun sendsDownWhenControlDisabled() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    )

    runner.applyNow()

    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun downsCompanionWhenDisabled() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val tunnel = RecordingTunnel()
    ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    ).applyNow()
    assertEquals(
      listOf("office" to TunnelCommand.DOWN, "travel" to TunnelCommand.DOWN),
      tunnel.commands,
    )
  }

  @Test
  fun skipDoesNotSendWhenTunnelBlank() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = ""))
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
      StoredControl(enabled = false, pausedUntilEpochMillis = 5L, tunnelName = "office", mobileTunnelName = "office"),
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "office"))
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
      store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "office")),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = RecordingTunnel(),
      log = log,
    ).applyNow("debounce")
    assertEquals(LogKind.DEBOUNCE, log.entries.single().first)
  }

  @Test
  fun skipsUnreadableWifiWithoutRetry() {
    val tunnel = RecordingTunnel()
    ApplyRunner(
      store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office")),
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      tunnel = tunnel,
    ).applyNow("debounce")
    assertTrue(tunnel.commands.isEmpty())
  }
}
