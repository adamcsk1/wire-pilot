package com.wirepilot.app.control

import com.wirepilot.app.data.DiagnosticState
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.data.TunnelCatalog
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.InMemoryDiagnosticStore
import com.wirepilot.app.support.InMemorySplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.support.InMemoryExcludedSsidStore
import com.wirepilot.app.support.InMemoryTunnelCatalog
import com.wirepilot.app.support.RecordingLog
import com.wirepilot.app.support.RecordingPauseAlarm
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeControllerTest {
  private val now = 10_000L

  private fun controller(
    initial: StoredControl = StoredControl(enabled = true, tunnelName = "office"),
    network: NetworkSnapshot = NetworkSnapshot(NetworkKind.MOBILE),
    log: DiagnosticLog = NoOpDiagnosticLog,
    tunnelState: TunnelStatePort = NoOpTunnelState,
    reconcileNetworkMonitor: () -> Unit = {},
  ): Triple<HomeController, InMemoryControlStore, RecordingPauseAlarm> {
    val store = InMemoryControlStore(initial)
    val alarms = RecordingPauseAlarm()
    val ssids = InMemoryExcludedSsidStore()
    if (initial.tunnelName.isNotBlank() && initial.excludedSsids.isNotEmpty()) {
      ssids.write(initial.tunnelName, initial.excludedSsids)
    }
    val runner = ApplyRunner(
      store,
      { now },
      { network },
      RecordingTunnel(),
      log,
      excludedSsidsFor = { name -> ssids.read(name) },
    )
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = runner,
      pauseAlarms = alarms,
      network = { network },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      excludedSsids = ssids,
      tunnelState = tunnelState,
      reconcileNetworkMonitor = reconcileNetworkMonitor,
    )
    return Triple(home, store, alarms)
  }

  @Test
  fun viewStateSortsSsidsAndShowsWatching() {
    val (home) = controller(StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Zed", "Able")))
    val state = home.viewState()
    assertEquals("office", state.tunnelName)
    assertEquals(listOf("Able", "Zed"), state.excludedSsids)
    assertEquals(StatusPresentation.Watching, state.status)
    assertEquals(PolicyLine(PolicyLineKind.MOBILE_DOWN, "office"), state.policyLine)
    assertEquals(ApplyNowAction.APPLY, state.applyNow.action)
    assertTrue(state.applyNow.enabled)
    assertTrue(state.applyNow.visible)
    assertFalse(state.connectOnMobile)
    assertEquals(ControlSelection.ON, state.controlSelection)
    assertFalse(state.policyLoggingEnabled)
    assertFalse(state.vpnLoggingEnabled)
    assertFalse(state.usageEnabled)
  }

  @Test
  fun viewStateShowsDisconnectOnExcludedSsid() {
    val (home) = controller(
      StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Home")),
      network = NetworkSnapshot(NetworkKind.WIFI, setOf("Home")),
    )
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.WIFI_EXCLUDED_DOWN, "office", "Home"), state.policyLine)
    assertEquals(ApplyNowAction.APPLY, state.applyNow.action)
    assertTrue(state.applyNow.enabled)
    assertTrue(state.applyNow.visible)
  }

  @Test
  fun viewStateShowsWifiUpPolicyLine() {
    val (home) = controller(
      StoredControl(enabled = true, tunnelName = "office"),
      network = NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")),
    )
    assertEquals(PolicyLine(PolicyLineKind.WIFI_UP, "office", "Cafe"), home.viewState().policyLine)
  }

  @Test
  fun viewStateShowsMobileUpPolicyLine() {
    val (home) = controller(
      StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "travel"),
      network = NetworkSnapshot(NetworkKind.MOBILE),
    )
    assertEquals(PolicyLine(PolicyLineKind.MOBILE_UP, "travel"), home.viewState().policyLine)
  }

  @Test
  fun viewStateAppliesDownWhenDisabled() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = "office"))
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.CONTROL_OFF), state.policyLine)
    assertEquals(ApplyNowAction.UNAVAILABLE, state.applyNow.action)
    assertFalse(state.applyNow.enabled)
    assertFalse(state.applyNow.visible)
  }

  @Test
  fun viewStateShowsConnectedWhenDisabledAndTunnelUp() {
    val (home) = controller(
      StoredControl(enabled = false, tunnelName = "office"),
      tunnelState = TunnelStatePort { name -> name == "office" },
    )
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.CONTROL_OFF_CONNECTED, "office"), state.policyLine)
    assertTrue(state.vpnConnected)
    assertEquals("office", state.connectedTunnelName)
    assertFalse(state.applyNow.visible)
  }

  @Test
  fun viewStateShowsConnectedWhenDisabledAndMobileUp() {
    val (home) = controller(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
      tunnelState = TunnelStatePort { name -> name == "travel" },
    )
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.CONTROL_OFF_CONNECTED, "travel"), state.policyLine)
    assertEquals("travel", state.connectedTunnelName)
  }

  @Test
  fun viewStateShowsPausedPolicyLine() {
    val (home) = controller(
      StoredControl(enabled = false, pausedUntilEpochMillis = now + 60_000L, tunnelName = "office"),
    )
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.PAUSED), state.policyLine)
    assertFalse(state.applyNow.visible)
  }

  @Test
  fun viewStateShowsConnectedWhenPausedAndTunnelUp() {
    val (home) = controller(
      StoredControl(enabled = false, pausedUntilEpochMillis = now + 60_000L, tunnelName = "office"),
      tunnelState = TunnelStatePort { name -> name == "office" },
    )
    assertEquals(
      PolicyLine(PolicyLineKind.PAUSED_CONNECTED, "office"),
      home.viewState().policyLine,
    )
  }

  @Test
  fun viewStateHidesApplyNowWhenDisabledAndTunnelBlank() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = ""))
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.NO_TUNNEL), state.policyLine)
    assertEquals(ApplyNowAction.UNAVAILABLE, state.applyNow.action)
    assertFalse(state.applyNow.enabled)
    assertFalse(state.applyNow.visible)
    assertNull(state.applyNow.skipReason)
  }

  @Test
  fun viewStateDisablesApplyNowWhenWatchingWithoutTunnel() {
    val (home) = controller(StoredControl(enabled = true, tunnelName = ""))
    val state = home.viewState()
    assertEquals(PolicyLine(PolicyLineKind.NO_TUNNEL), state.policyLine)
    assertEquals(ApplyNowAction.UNAVAILABLE, state.applyNow.action)
    assertFalse(state.applyNow.enabled)
    assertTrue(state.applyNow.visible)
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
    var monitorReconciles = 0
    val (home, store) = controller(reconcileNetworkMonitor = { monitorReconciles += 1 })
    home.setTunnelName("  office-vpn  ")
    assertEquals("office-vpn", store.read().tunnelName)
    assertEquals(1, monitorReconciles)
  }

  @Test
  fun setSameTunnelNameDoesNotReconcileMonitor() {
    var monitorReconciles = 0
    val (home, store) = controller(reconcileNetworkMonitor = { monitorReconciles += 1 })
    home.setTunnelName(" office ")
    assertEquals("office", store.read().tunnelName)
    assertEquals(0, monitorReconciles)
  }

  @Test
  fun rejectsInvalidTunnelName() {
    var monitorReconciles = 0
    val (home, store) = controller(
      StoredControl(enabled = true, tunnelName = "office"),
      reconcileNetworkMonitor = { monitorReconciles += 1 },
    )
    home.setTunnelName("bad/name")
    assertEquals("office", store.read().tunnelName)
    assertEquals(0, monitorReconciles)
  }

  @Test
  fun selectImportedTunnelApplies() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "[Interface]\n"))
    val log = RecordingLog()
    var monitorReconciles = 0
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
      reconcileNetworkMonitor = { monitorReconciles += 1 },
    )
    home.selectImportedTunnel("missing")
    assertEquals("office", store.read().tunnelName)
    home.selectImportedTunnel("HomeVPN")
    assertEquals("HomeVPN", store.read().tunnelName)
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
    assertEquals(1, monitorReconciles)
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
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
    val (home) = controller(StoredControl(enabled = true, tunnelName = "office", excludedSsids = setOf("Home")))
    assertFalse(home.addExcludedSsid("  "))
    assertFalse(home.addExcludedSsid("Home"))
  }

  @Test
  fun addAndRemoveExcludedSsid() {
    val (home, store) = controller()
    assertTrue(home.addExcludedSsid("\"Home\""))
    assertEquals(setOf("Home"), home.excludedSsids("office"))
    home.removeExcludedSsid("Home")
    assertEquals(emptySet(), home.excludedSsids("office"))
  }

  @Test
  fun addExcludedSsidOnOtherTunnelDoesNotApply() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val ssids = InMemoryExcludedSsidStore()
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      excludedSsids = ssids,
    )
    assertTrue(home.addExcludedSsid("Cafe", "HomeVPN"))
    assertEquals(setOf("Cafe"), home.excludedSsids("HomeVPN"))
    assertEquals(emptySet(), home.excludedSsids("office"))
    assertTrue(log.entries.none { it.second.contains("trigger=ssid-add") })
    assertFalse(home.addExcludedSsid("Cafe", ""))
  }

  @Test
  fun removeExcludedSsidOnOtherTunnelDoesNotApply() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val ssids = InMemoryExcludedSsidStore()
    ssids.write("HomeVPN", setOf("Cafe"))
    ssids.write("office", setOf("Home"))
    val log = RecordingLog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      excludedSsids = ssids,
    )
    home.removeExcludedSsid("Cafe", "")
    assertEquals(setOf("Cafe"), home.excludedSsids("HomeVPN"))
    home.removeExcludedSsid("Cafe", "HomeVPN")
    assertEquals(emptySet(), home.excludedSsids("HomeVPN"))
    assertEquals(setOf("Home"), home.excludedSsids("office"))
    assertTrue(log.entries.none { it.second.contains("trigger=ssid-remove") })
  }

  @Test
  fun saveTunnelRunsSsidMigration() {
    var migrated = 0
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
    val catalog = InMemoryTunnelCatalog()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      ssidMigration = { migrated += 1 },
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "[Interface]"))
    assertEquals(1, migrated)
  }

  @Test
  fun reloadImportedRunsSsidMigration() {
    var migrated = 0
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val monitorModes = mutableListOf<NetworkMonitorMode>()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      ssidMigration = { migrated += 1 },
      reconcileNetworkMonitor = { monitorModes += NetworkMonitorPolicy.mode(store.read(), now) },
    )
    home.reloadImported(emptyList())
    assertEquals(0, migrated)
    assertTrue(monitorModes.isEmpty())
    home.reloadImported(listOf("office"))
    assertEquals(1, migrated)
    assertEquals(listOf(NetworkMonitorMode.WATCHING), monitorModes)
  }

  @Test
  fun enableControlCancelsPauseWithoutApplying() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = 99_000L, tunnelName = "office"),
    )
    val alarms = RecordingPauseAlarm()
    val tunnel = RecordingTunnel()
    val monitorModes = mutableListOf<NetworkMonitorMode>()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      reconcileNetworkMonitor = { monitorModes += NetworkMonitorPolicy.mode(store.read(), now) },
    )
    home.enableControl()
    assertEquals(true, store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertEquals(1, alarms.cancelCount)
    assertTrue(tunnel.commands.isEmpty())
    assertEquals(listOf(NetworkMonitorMode.WATCHING), monitorModes)
  }

  @Test
  fun disableForeverCancelsAlarmAndAppliesDown() {
    val log = RecordingLog()
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val alarms = RecordingPauseAlarm()
    val tunnel = RecordingTunnel()
    val monitorModes = mutableListOf<NetworkMonitorMode>()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      reconcileNetworkMonitor = { monitorModes += NetworkMonitorPolicy.mode(store.read(), now) },
    )
    home.disableControlForever()
    assertEquals(false, store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertEquals(1, alarms.cancelCount)
    assertEquals(LogKind.DISABLE, log.entries.first().first)
    assertEquals("always", log.entries.first().second)
    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
    assertEquals(listOf(NetworkMonitorMode.STOPPED), monitorModes)
  }

  @Test
  fun timedPauseSchedulesAlarmAndAppliesDown() {
    val log = RecordingLog()
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val alarms = RecordingPauseAlarm()
    val tunnel = RecordingTunnel()
    val monitorModes = mutableListOf<NetworkMonitorMode>()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel, log),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      reconcileNetworkMonitor = { monitorModes += NetworkMonitorPolicy.mode(store.read(), now) },
    )
    home.pauseFor(PauseOption.HOURS_1)
    assertEquals(false, store.read().enabled)
    assertEquals(now + PauseOption.HOURS_1.durationMillis!!, alarms.scheduledAt)
    assertEquals(LogKind.PAUSE, log.entries.first().first)
    assertEquals("HOURS_1", log.entries.first().second)
    assertEquals(listOf("office" to TunnelCommand.DOWN), tunnel.commands)
    assertEquals(listOf(NetworkMonitorMode.PAUSED), monitorModes)
  }

  @Test
  fun alwaysPauseCancelsAlarm() {
    val monitorModes = mutableListOf<NetworkMonitorMode>()
    lateinit var store: InMemoryControlStore
    val result = controller(reconcileNetworkMonitor = {
      monitorModes += NetworkMonitorPolicy.mode(store.read(), now)
    })
    val home = result.first
    store = result.second
    val alarms = result.third
    home.pauseFor(PauseOption.ALWAYS)
    assertEquals(null, alarms.scheduledAt)
    assertEquals(1, alarms.cancelCount)
    assertEquals(listOf(NetworkMonitorMode.STOPPED), monitorModes)
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val diagnostics = InMemoryDiagnosticStore(DiagnosticState(policyEnabled = true, vpnEnabled = true))
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
  fun disconnectManuallyDownsMobileCompanion() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val tunnel = RecordingTunnel()
    tunnel.send("travel", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      tunnelState = tunnel,
    )
    assertTrue(home.viewState().vpnConnected)
    home.disconnectManually()
    assertEquals(
      listOf(
        "travel" to TunnelCommand.UP,
        "office" to TunnelCommand.DOWN,
        "travel" to TunnelCommand.DOWN,
      ),
      tunnel.commands,
    )
    assertFalse(home.viewState().vpnConnected)
  }

  @Test
  fun connectManuallyOnMobileUsesMobileTunnel() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
    )
    home.connectManually()
    assertEquals(listOf("travel" to TunnelCommand.UP), tunnel.commands)
  }

  @Test
  fun setConnectOnMobilePersistsAndApplies() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "travel" to "y"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    home.setConnectOnMobile(true, "travel")
    assertEquals("travel", store.read().mobileTunnelName)
    assertTrue(home.viewState().connectOnMobile)
    home.setConnectOnMobile(false, "travel")
    assertEquals("", store.read().mobileTunnelName)
    assertFalse(home.viewState().connectOnMobile)
  }

  @Test
  fun deleteMobileTunnelClearsMobileName() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "travel" to "y"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    home.deleteImportedTunnel("travel")
    assertEquals("office", store.read().tunnelName)
    assertEquals("", store.read().mobileTunnelName)
  }

  @Test
  fun controlSelectionFollowsDisabled() {
    val (home) = controller(StoredControl(enabled = false, tunnelName = "office"))
    assertEquals(ControlSelection.OFF, home.viewState().controlSelection)
  }

  @Test
  fun vpnConnectedFollowsTunnelState() {
    val (home) = controller(
      StoredControl(enabled = true, tunnelName = "office"),
      tunnelState = TunnelStatePort { name -> name == "office" },
    )
    val state = home.viewState()
    assertTrue(state.vpnConnected)
    assertEquals("office", state.connectedTunnelName)
  }

  @Test
  fun vpnConnectedFalseWhenTunnelBlank() {
    val (home) = controller(
      StoredControl(enabled = true, tunnelName = ""),
      tunnelState = TunnelStatePort { true },
    )
    val state = home.viewState()
    assertFalse(state.vpnConnected)
    assertEquals("", state.connectedTunnelName)
  }

  @Test
  fun vpnConnectedFalseWhenTunnelDown() {
    val (home) = controller(StoredControl(enabled = true, tunnelName = "office"))
    val state = home.viewState()
    assertFalse(state.vpnConnected)
    assertEquals("", state.connectedTunnelName)
  }

  @Test
  fun tunnelRowsMarkSelectedAndUp() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
  fun tunnelRowsIncludeSplitAndSsidCounts() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "b"))
    val splits = InMemorySplitTunnelStore()
    splits.write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo", "com.bar")))
    val ssids = InMemoryExcludedSsidStore()
    ssids.write("office", setOf("Home"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      splitTunnels = splits,
      excludedSsids = ssids,
    )
    assertEquals(
      listOf(
        TunnelRow(
          name = "office",
          selected = true,
          up = false,
          splitMode = SplitTunnelMode.EXCLUDE_APPS,
          splitAppCount = 2,
          excludedSsidCount = 1,
        ),
      ),
      home.viewState().tunnelRows,
    )
  }

  @Test
  fun saveFirstTunnelSelectsIt() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
    val catalog = InMemoryTunnelCatalog()
    val log = RecordingLog()
    val monitorStates = mutableListOf<StoredControl>()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel(), log),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      log = log,
      catalog = catalog,
      reconcileNetworkMonitor = { monitorStates += store.read() },
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "[Interface]"))
    assertEquals("office", store.read().tunnelName)
    assertEquals("office", store.read().mobileTunnelName)
    assertEquals(listOf("office"), catalog.names())
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
    assertEquals(listOf(store.read()), monitorStates)
  }

  @Test
  fun saveTunnelWriteFailure() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    var monitorReconciles = 0
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      reconcileNetworkMonitor = { monitorReconciles += 1 },
    )
    assertEquals(TunnelSaveResult.INVALID_NAME, home.saveTunnel("bad/name", "[Interface]"))
    assertEquals(TunnelSaveResult.INVALID_CONF, home.saveTunnel("home", "  "))
    assertEquals("office", store.read().tunnelName)
    assertEquals(listOf("office"), catalog.names())
    assertEquals(0, monitorReconciles)
  }

  @Test
  fun saveRejectsNameInUseOnCreateAndRename() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old"))
    val splits = InMemorySplitTunnelStore()
    splits.write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")))
    val ssids = InMemoryExcludedSsidStore()
    ssids.write("office", setOf("Home"))
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
      excludedSsids = ssids,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("HomeVPN", "new", "office"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals("HomeVPN", store.read().mobileTunnelName)
    assertEquals("new", catalog.readConf("HomeVPN"))
    assertEquals(null, catalog.readConf("office"))
    assertEquals(StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")), splits.read("HomeVPN"))
    assertEquals(StoredSplitTunnel(), splits.read("office"))
    assertEquals(setOf("Home"), home.excludedSsids("HomeVPN"))
    assertEquals(emptySet(), home.excludedSsids("office"))
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-save") })
  }

  @Test
  fun renameDownsPreviousWhenPolicySkips() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a"))
    val log = RecordingLog()
    val tunnel = RecordingTunnel()
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
    home.reloadImported(emptyList())
    assertEquals("", store.read().tunnelName)
    home.reloadImported(listOf("HomeVPN"))
    assertEquals("HomeVPN", store.read().tunnelName)
    assertEquals("HomeVPN", store.read().mobileTunnelName)
    assertEquals(listOf("HomeVPN" to TunnelCommand.UP), tunnel.commands)
    assertTrue(log.entries.any { it.second.contains("trigger=tunnel-select") })
  }

  @Test
  fun reloadImportedReupsMobileWhenDefaultOmitted() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "travel" to "y"))
    val tunnel = RecordingTunnel()
    tunnel.send("travel", TunnelCommand.UP)
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
    home.reloadImported(listOf("travel"))
    assertEquals(
      listOf("travel" to TunnelCommand.UP, "travel" to TunnelCommand.UP),
      tunnel.commands,
    )
  }

  @Test
  fun saveFirstTunnelOnMobileUps() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = ""))
    val catalog = InMemoryTunnelCatalog()
    val tunnel = RecordingTunnel()
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
    )
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("office", "[Interface]"))
    assertEquals("office", store.read().mobileTunnelName)
    assertEquals(listOf("office" to TunnelCommand.UP), tunnel.commands)
  }

  @Test
  fun renameMobileTunnelReupsWhenNotDefault() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "travel" to "y"))
    val tunnel = RecordingTunnel()
    tunnel.send("travel", TunnelCommand.UP)
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
    assertEquals(TunnelSaveResult.SAVED, home.saveTunnel("trip", "moved", "travel"))
    assertEquals("office", store.read().tunnelName)
    assertEquals("trip", store.read().mobileTunnelName)
    assertEquals(
      listOf(
        "travel" to TunnelCommand.UP,
        "travel" to TunnelCommand.DOWN,
        "trip" to TunnelCommand.UP,
      ),
      tunnel.commands,
    )
  }

  @Test
  fun reloadImportedAppliesWhenEnabled() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val ssids = InMemoryExcludedSsidStore()
    ssids.write("office", setOf("Home"))
    val tunnel = RecordingTunnel()
    val alarms = RecordingPauseAlarm()
    val monitorStates = mutableListOf<StoredControl>()
    tunnel.send("office", TunnelCommand.UP)
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, tunnel),
      pauseAlarms = alarms,
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      splitTunnels = splits,
      excludedSsids = ssids,
      tunnelState = tunnel,
      reconcileNetworkMonitor = { monitorStates += store.read() },
    )
    home.deleteImportedTunnel("office")
    assertEquals("", store.read().tunnelName)
    assertEquals("", store.read().mobileTunnelName)
    assertFalse(store.read().enabled)
    assertEquals(null, store.read().pausedUntilEpochMillis)
    assertTrue(alarms.cancelCount >= 1)
    assertTrue(catalog.names().isEmpty())
    assertEquals(StoredSplitTunnel(), splits.read("office"))
    assertEquals(emptySet(), home.excludedSsids("office"))
    assertEquals(
      listOf("office" to TunnelCommand.UP, "office" to TunnelCommand.DOWN),
      tunnel.commands,
    )
    assertEquals(listOf(store.read()), monitorStates)
  }

  @Test
  fun deleteSelectedPicksNext() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("HomeVPN" to "a", "office" to "b"))
    var monitorReconciles = 0
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      catalog = catalog,
      reconcileNetworkMonitor = { monitorReconciles += 1 },
    )
    home.deleteImportedTunnel("HomeVPN")
    home.deleteImportedTunnel("missing")
    assertEquals("office", store.read().tunnelName)
    assertEquals(listOf("office"), catalog.names())
    assertEquals(1, monitorReconciles)
  }

  @Test
  fun splitSettingsReadsNamedTunnel() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
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

  @Test
  fun setSplitTunnelOnMobileTunnelApplies() {
    val store = InMemoryControlStore(
      StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "travel"),
    )
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x", "travel" to "y"))
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
      catalog = catalog,
      splitTunnels = splits,
    )
    home.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"), "travel")
    assertEquals(setOf("com.foo"), splits.read("travel").packages)
    assertTrue(log.entries.any { it.second.contains("trigger=split-tunnel") })
    assertEquals(
      listOf(false, true),
      home.viewState().tunnelRows.sortedBy { row -> row.name }.map { row -> row.mobile },
    )
  }

  @Test
  fun usageSnapshotStaysOffUntilEnabled() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val home = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(),
      tunnelState = TunnelStatePort { true },
      tunnelStats = TunnelStatsPort { TunnelTraffic(100L, 200L) },
    )
    assertEquals(UsageSnapshot(enabled = false), home.usageSnapshot())
    home.setUsageEnabled(true)
    assertTrue(home.viewState().usageEnabled)
    assertEquals(
      UsageSnapshot(enabled = true, connected = true, tunnelName = "office", rxBytes = 100L, txBytes = 200L),
      home.usageSnapshot(),
    )
  }

  @Test
  fun usageSnapshotWhenDownOrMissingStats() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office", mobileTunnelName = "travel"))
    val down = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(DiagnosticState(usageEnabled = true)),
    )
    assertEquals(UsageSnapshot(enabled = true, connected = false), down.usageSnapshot())
    val upNoStats = HomeController(
      store = store,
      clock = { now },
      applyRunner = ApplyRunner(store, { now }, { NetworkSnapshot(NetworkKind.MOBILE) }, RecordingTunnel()),
      pauseAlarms = RecordingPauseAlarm(),
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      diagnostics = InMemoryDiagnosticStore(DiagnosticState(usageEnabled = true)),
      tunnelState = TunnelStatePort { name -> name == "travel" },
    )
    assertEquals(
      UsageSnapshot(enabled = true, connected = true, tunnelName = "travel", rxBytes = 0L, txBytes = 0L),
      upNoStats.usageSnapshot(),
    )
  }
}
