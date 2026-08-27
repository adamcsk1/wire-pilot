package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.InMemoryExcludedSsidStore
import com.wirepilot.app.support.InMemorySplitTunnelStore
import com.wirepilot.app.support.InMemoryTunnelCatalog
import com.wirepilot.app.support.RecordingPauseAlarm
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelInventoryTest {
  private val now = 10_000L

  private fun inventory(
    initial: StoredControl = StoredControl(enabled = true, tunnelName = "office"),
    catalog: InMemoryTunnelCatalog = InMemoryTunnelCatalog(mapOf("office" to "[Interface]")),
    tunnelState: TunnelStatePort = NoOpTunnelState,
  ): Triple<TunnelInventory, InMemoryControlStore, RecordingTunnel> {
    val store = InMemoryControlStore(initial)
    val tunnel = RecordingTunnel()
    val commands = TunnelInventory(
      store = store,
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      catalog = catalog,
      splitTunnels = InMemorySplitTunnelStore(),
      excludedSsids = InMemoryExcludedSsidStore(),
      tunnelState = tunnelState,
      ssidMigration = {},
      resolver = ControlResolver(store) { now },
    )
    return Triple(commands, store, tunnel)
  }

  @Test
  fun selectUnknownTunnelDoesNothing() {
    val (commands, store, tunnel) = inventory()
    commands.selectImportedTunnel("missing")
    assertEquals("office", store.read().tunnelName)
    assertTrue(tunnel.commands.isEmpty())
  }

  @Test
  fun saveRejectsBlankConf() {
    val (commands) = inventory()
    assertEquals(TunnelSaveResult.INVALID_CONF, commands.saveTunnel("travel", "  "))
  }

  @Test
  fun reloadEmptyDoesNothing() {
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val (commands, _, tunnel) = inventory(catalog = catalog)
    commands.reloadImported(emptyList())
    assertTrue(tunnel.commands.isEmpty())
  }
}
