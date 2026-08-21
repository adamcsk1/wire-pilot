package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.data.TunnelCatalog
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.InMemoryDiagnosticStore
import com.wirepilot.app.support.InMemorySplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.support.InMemoryTunnelCatalog
import com.wirepilot.app.support.RecordingLog
import com.wirepilot.app.support.RecordingPauseAlarm
import com.wirepilot.app.support.RecordingTunnel
import com.wirepilot.app.support.RecordingWatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeControllerTest {
  private val now = 10_000L

  private fun controller(
    initial: StoredControl = StoredControl(tunnelName = "office"),
    network: NetworkSnapshot = NetworkSnapshot(NetworkKind.MOBILE),
    log: DiagnosticLog = NoOpDiagnosticLog,
    watching: WatchingServicePort = NoOpWatchingService,
    tunnelState: TunnelStatePort = NoOpTunnelState,
  ): Triple<HomeController, InMemoryControlStore, RecordingPauseAlarm> {
    val store = InMemoryControlStore(initial)
    val alarms = RecordingPauseAlarm()
    val runner = ApplyRunner(store, { now }, { network }, RecordingTunnel(), log)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = runner,
      pauseAlarms = alarms,
      network = { network },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      watching = watching,
      tunnelState = tunnelState,
    )
    return Triple(home, store, alarms)
  }

  @Test
  fun viewStateSortsSsidsAndShowsWatching() {
    val (home) = controller(StoredControl(tunnelName = "office", excludedSsids = setOf("Zed", "Able")))
    val state = home.viewState()
    assertEquals("office", state.tunnelName)
    assertEquals(listOf("Able", "Zed"), state.excludedSsids)
    assertEquals(StatusPresentation.Watching, state.status)
    assertEquals(ApplyNowAction.APPLY, state.applyNow.action)
    assertTrue(state.applyNow.enabled)
    assertTrue(state.connectOnMobile)
    assertEquals(ControlSelection.ON, state.controlSelection)
  }

  @Test
  fun viewStateShowsDisconnectOnExcludedSsid() {
    val (home) = controller(
      StoredControl(tunnelName = "office", excludedSsids = setOf("Home")),
      network = NetworkSnapshot(NetworkKind.WIFI, setOf("Home")),
    )
    val state = home.viewState()
    assertEquals(ApplyNowAction.APPLY, state.applyNow.action)
    assertTrue(state.applyNow.enabled)
  }

  @Test
  fun viewStateAppliesDownWhenDisabled() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = "office"))
    val state = home.viewState()
    assertEquals(ApplyNowAction.APPLY, state.applyNow.action)
    assertTrue(state.applyNow.enabled)
  }

  @Test
  fun viewStateDisablesApplyNowWhenTunnelBlank() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = ""))
    val state = home.viewState()
    assertEquals(ApplyNowAction.UNAVAILABLE, state.applyNow.action)
    assertFalse(state.applyNow.enabled)
    assertEquals(SkipReason.BLANK_TUNNEL_NAME, state.applyNow.skipReason)
  }

  @Test
  fun viewStatePersistsExpiredPause() {
    val (home, store) = controller(
      StoredControl(enabled = false, pausedUntilEpochMillis = 5L, tunnelName = "office"),
    )
    assertEquals(StatusPresentation.Watching, home.viewState().status)
    assertEquals(true, store.read().enabled)
  }

  @Test
  fun setTunnelNameTrims() {
    val (home, store) = controller()
    home.setTunnelName("  office-vpn  ")
    assertEquals("office-vpn", store.read().tunnelName)
  }

  @Test
  fun rejectsInvalidTunnelName() {
    val (home, store) = controller(StoredControl(tunnelName = "office"))
    home.setTunnelName("bad/name")
    assertEquals("office", store.read().tunnelName)
  }

  @Test
  fun selectImportedTunnelApplies() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "[Interface]\n"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    home.selectImportedTunnel("missing")
    assertEquals("office", store.read().tunnelName)
    home.selectImportedTunnel("HomeVPN")
    assertEquals("HomeVPN", store.read().tunnelName)
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
  }

  @Test
  fun selectImportedTunnelWhileOffDownsPrevious() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "HomeVPN" to "y"))
    val tunnel = RecordingTunnel()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    home.selectImportedTunnel("HomeVPN")
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun setSplitTunnelPersistsAndApplies() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val splits = InMemorySplitTunnelStore()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      splitTunnels = splits,
    )
    home.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"))
    assertEquals(SplitTunnelMode.EXCLUDE_APPS, splits.read("office").mode)
    assertEquals(setOf("com.foo"), splits.read("office").packages)
    assertTrue(log.entries.any { it.second.contains("trigger=split-tunnel") })
  }

  @Test
  fun setSplitTunnelIgnoredWhenNoTunnel() {
    val store = InMemoryControlStore(StoredControl(tunnelName = ""))
    val splits = InMemorySplitTunnelStore()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      splitTunnels = splits,
    )
    home.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"))
    assertEquals(StoredSplitTunnel(), splits.read(""))
  }

  @Test
  fun addExcludedSsidReturnsFalseWhenUnchanged() {
    val (home) = controller(StoredControl(excludedSsids = setOf("Home")))
    assertFalse(home.addExcludedSsid("  "))
    assertFalse(home.addExcludedSsid("Home"))
  }

  @Test
  fun addAndRemoveExcludedSsid() {
    val (home, store) = controller()
    assertTrue(home.addExcludedSsid("\"Home\""))
    assertEquals(setOf("Home"), store.read().excludedSsids)
    home.removeExcludedSsid("Home")
    assertEquals(emptySet(), store.read().excludedSsids)
  }

  @Test
  fun enableControlCancelsPauseAndApplies() {
    val (home, store, alarms) = controller(
      StoredControl(enabled = false, pausedUntilEpochMillis = 99_000L, tunnelName = "office"),
    )
    home.enableControl()
    assertEquals(true, store.read().enabled)
    assertEquals(1, alarms.cancelCount)
  }

  @Test
  fun disableForeverCancelsAlarmAndAppliesDown() {
    val log = RecordingLog()
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val alarms = RecordingPauseAlarm()
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
    )
    home.disableControlForever()
    assertEquals(false, store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertEquals(1, alarms.cancelCount)
    assertEquals(LogKind.DISABLE, log.entries.first().first)
    assertEquals("always", log.entries.first().second)
    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun timedPauseSchedulesAlarmAndAppliesDown() {
    val log = RecordingLog()
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val alarms = RecordingPauseAlarm()
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
    )
    home.pauseFor(PauseOption.HOURS_1)
    assertEquals(false, store.read().enabled)
    assertEquals(now + PauseOption.HOURS_1.durationMillis!!, alarms.scheduledAt)
    assertEquals(LogKind.PAUSE, log.entries.first().first)
    assertEquals("HOURS_1", log.entries.first().second)
    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
  }

  @Test
  fun alwaysPauseCancelsAlarm() {
    val (home, _, alarms) = controller()
    home.pauseFor(PauseOption.ALWAYS)
    assertEquals(null, alarms.scheduledAt)
    assertEquals(1, alarms.cancelCount)
  }

  @Test
  fun applyNowUsesApplyNowTrigger() {
    val log = RecordingLog()
    val (home) = controller(log = log)
    home.applyNow()
    assertEquals(LogKind.APPLY_NOW, log.entries.single().first)
    assertTrue(log.entries.single().second.contains("trigger=apply-now"))
  }

  @Test
  fun loggingCanBeDisabledAndCleared() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val diagnostics = InMemoryDiagnosticStore()
    val logger = DiagnosticLogger(diagnostics) { now }
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), logger),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = diagnostics,
      log = logger,
    )
    home.applyNow()
    val logged = home.viewState()
    assertTrue(logged.logCopyText.isNotBlank())
    assertTrue(logged.logPreview.startsWith("showing last 1 of 1"))
    home.setLoggingEnabled(false)
    assertFalse(home.viewState().loggingEnabled)
    home.clearLogs()
    assertEquals("", home.viewState().logCopyText)
  }

  @Test
  fun manualConnectOnlyWhenPolicyOff() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val tunnel = RecordingTunnel()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
    )
    home.connectManually()
    home.disconnectManually()
    assertEquals(listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.DOWN), tunnel.commands)
    store.write(store.read().copy(enabled = true))
    home.connectManually()
    assertEquals(2, tunnel.commands.size)
  }

  @Test
  fun setConnectOnMobilePersistsAndApplies() {
    val (home, store) = controller()
    home.setConnectOnMobile(false)
    assertFalse(store.read().connectOnMobile)
    assertFalse(home.viewState().connectOnMobile)
  }

  @Test
  fun controlSelectionFollowsDisabled() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = "office"))
    assertEquals(ControlSelection.OFF, home.viewState().controlSelection)
  }

  @Test
  fun watchingStartsWhenEnabledAndStopsWhenDisabled() {
    val watching = RecordingWatching()
    val (home) = controller(
      StoredControl(enabled = false, tunnelName = "office"),
      watching = watching,
    )
    home.enableControl()
    assertEquals(true, watching.values.last())
    home.disableControlForever()
    assertEquals(false, watching.values.last())
  }

  @Test
  fun vpnConnectedFollowsTunnelState() {
    val (home) = controller(
      StoredControl(tunnelName = "office"),
      tunnelState = TunnelStatePort { name -> name == "office" },
    )
    assertTrue(home.viewState().vpnConnected)
  }

  @Test
  fun vpnConnectedFalseWhenTunnelBlank() {
    val (home) = controller(
      StoredControl(tunnelName = ""),
      tunnelState = TunnelStatePort { true },
    )
    assertFalse(home.viewState().vpnConnected)
  }

  @Test
  fun vpnConnectedFalseWhenTunnelDown() {
    val (home) = controller(StoredControl(tunnelName = "office"))
    assertFalse(home.viewState().vpnConnected)
  }

  @Test
  fun tunnelRowsMarkSelectedAndUp() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = TunnelStatePort { name -> name == "office" },
    )
    assertEquals(
      listOf(
        TunnelRow(name = "HomeVPN", selected = false, up = false),
        TunnelRow(name = "office", selected = true, up = true),
      ),
      home.viewState().tunnelRows,
    )
  }

  @Test
  fun saveFirstTunnelSelectsIt() {
    val store = InMemoryControlStore(StoredControl(tunnelName = ""))
    val catalog = InMemoryTunnelCatalog()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "[Interface]"))
    assertEquals("office", store.read().tunnelName)
    assertEquals(listOf("office"), catalog.names())
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
  }

  @Test
  fun saveTunnelWriteFailure() {
    val store = InMemoryControlStore(StoredControl(tunnelName = ""))
    val catalog = object : TunnelCatalog {
      override fun names(): List<String> = emptyList()
      override fun readConf(name: String): String? = null
      override fun writeConf(name: String, conf: String) {
        error("disk")
      }
      override fun delete(name: String) = Unit
    }
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.WRITE_FAILED, home.saveTunnel("office", "[Interface]"))
    assertEquals("", store.read().tunnelName)
  }

  @Test
  fun saveRejectsInvalidNameAndBlankConf() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.INVALID_NAME, home.saveTunnel("bad/name", "[Interface]"))
    assertEquals(TunnelSaveResult.INVALID_CONF, home.saveTunnel("home", "  "))
    assertEquals("office", store.read().tunnelName)
    assertEquals(listOf("office"), catalog.names())
  }

  @Test
  fun saveRejectsNameInUseOnCreateAndRename() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.NAME_IN_USE, home.saveTunnel("HomeVPN", "[Interface]"))
    assertEquals(TunnelSaveResult.NAME_IN_USE, home.saveTunnel("HomeVPN", "[Interface]", "office"))
    assertEquals("b", catalog.readConf("office"))
  }

  @Test
  fun saveAdditionalTunnelLeavesDefault() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("HomeVPN", "[Peer]"))
    assertEquals("office", store.read().tunnelName)
    assertEquals("[Peer]", catalog.readConf("HomeVPN"))
    assertTrue(log.entries.none { it.second.contains("trigger=tunnel-save") })
  }

  @Test
  fun saveSelectedWhileEnabledApplies() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "new", "office"))
    assertEquals("new", catalog.readConf("office"))
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-save") })
  }

  @Test
  fun saveSelectedWhileOffReupsWhenConnected() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "new", "office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.UP),
      tunnel.commands,
    )
  }

  @Test
  fun saveSelectedWhileOffSkipsWhenDown() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "new", "office"))
    assertTrue(tunnel.commands.isEmpty())
  }

  @Test
  fun renameMovesSplitAndRetargetsDefault() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val splits = InMemorySplitTunnelStore()
    splits.write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
      splitTunnels = splits,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("HomeVPN", "new", "office"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals("new", catalog.readConf("HomeVPN"))
    assertEquals(null, catalog.readConf("office"))
    assertEquals(StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")), splits.read("HomeVPN"))
    assertEquals(StoredSplitTunnel(), splits.read("office"))
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-save") })
  }

  @Test
  fun renameDownsPreviousWhenPolicySkips() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.WIFI) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("HomeVPN", "new", "office"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals(
      listOf(
        "office" to TunnelCommand.UP,
        "office" to TunnelCommand.DOWN,
        "HomeVPN" to TunnelCommand.UP,
      ),
      tunnel.commands,
    )
  }

  @Test
  fun saveSelectedWhileSkipReupsLiveTunnel() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.WIFI) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "new", "office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.UP),
      tunnel.commands,
    )
  }

  @Test
  fun renameSelectedWhileOffReupsPrevious() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("HomeVPN", "new", "office"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals(
      listOf(
        "office" to TunnelCommand.UP,
        "office" to TunnelCommand.DOWN,
        "HomeVPN" to TunnelCommand.UP,
      ),
      tunnel.commands,
    )
  }

  @Test
  fun renameNonSelectedLeavesDefault() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("Travel", "moved", "HomeVPN"))
    assertEquals("office", store.read().tunnelName)
    assertEquals("moved", catalog.readConf("Travel"))
    assertEquals(null, catalog.readConf("HomeVPN"))
  }

  @Test
  fun reloadImportedSelectsFirstWhenBlank() {
    val store = InMemoryControlStore(StoredControl(tunnelName = ""))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    home.reloadImported(emptyList())
    assertEquals("", store.read().tunnelName)
    home.reloadImported(listOf("HomeVPN"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
  }

  @Test
  fun reloadImportedAppliesWhenEnabled() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    home.reloadImported(listOf("HomeVPN"))
    assertTrue(log.entries.none { it.second.contains("trigger=tunnel-import") })
    home.reloadImported(listOf("office"))
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-import") })
  }

  @Test
  fun reloadImportedReupsWhenSkipAndConnected() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.WIFI) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.WIFI) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    home.reloadImported(listOf("office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.UP),
      tunnel.commands,
    )
  }

  @Test
  fun reloadImportedReupsWhenOffAndConnected() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val tunnel = RecordingTunnel()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    home.reloadImported(listOf("office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.UP),
      tunnel.commands,
    )
  }

  @Test
  fun reloadImportedSkipsWhenOffAndDown() {
    val store = InMemoryControlStore(StoredControl(enabled = false, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      tunnelState = tunnel,
    )
    home.reloadImported(listOf("office"))
    assertTrue(tunnel.commands.isEmpty())
  }

  @Test
  fun deleteLastClearsNameAndDowns() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = now + 60_000L, tunnelName = "office"),
    )
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val splits = InMemorySplitTunnelStore()
    splits.write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")))
    val tunnel = RecordingTunnel()
    val alarms = RecordingPauseAlarm()
    val watching = RecordingWatching()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      watching = watching,
      catalog = catalog,
      splitTunnels = splits,
      tunnelState = tunnel,
    )
    home.deleteImportedTunnel("office")
    assertEquals("", store.read().tunnelName)
    assertFalse(store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertTrue(alarms.cancelCount >= 1)
    assertEquals(false, watching.values.last())
    assertTrue(catalog.names().isEmpty())
    assertEquals(StoredSplitTunnel(), splits.read("office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.DOWN),
      tunnel.commands,
    )
  }

  @Test
  fun deleteSelectedPicksNext() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
    )
    home.deleteImportedTunnel("office")
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals(listOf("HomeVPN"), catalog.names())
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
  }

  @Test
  fun deleteOtherLeavesDefault() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    home.deleteImportedTunnel("HomeVPN")
    home.deleteImportedTunnel("missing")
    assertEquals("office", store.read().tunnelName)
    assertEquals(listOf("office"), catalog.names())
  }

  @Test
  fun splitSettingsReadsNamedTunnel() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val splits = InMemorySplitTunnelStore()
    splits.write("HomeVPN", StoredSplitTunnel(SplitTunnelMode.INCLUDE_APPS, setOf("com.bar")))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      splitTunnels = splits,
    )
    assertEquals(
      StoredSplitTunnel(SplitTunnelMode.INCLUDE_APPS, setOf("com.bar")),
      home.splitSettings("HomeVPN"),
    )
  }

  @Test
  fun setSplitTunnelOnOtherNameDoesNotApply() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val splits = InMemorySplitTunnelStore()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      splitTunnels = splits,
    )
    home.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"), "HomeVPN")
    assertEquals(SplitTunnelMode.EXCLUDE_APPS, splits.read("HomeVPN").mode)
    assertTrue(log.entries.none { it.second.contains("trigger=split-tunnel") })
  }
}
