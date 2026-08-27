package com.wirepilot.app.platform

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wirepilot.app.control.AppLockSession
import com.wirepilot.app.control.ApplyRunner
import com.wirepilot.app.control.BootCoordinator
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.DiagnosticLogger
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.LogFormatter
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.control.NetworkChangeCoordinator
import com.wirepilot.app.control.NetworkMonitorCoordinator
import com.wirepilot.app.control.NetworkMonitorRuntime
import com.wirepilot.app.control.PauseExpiryCoordinator
import com.wirepilot.app.control.PauseRescheduler
import com.wirepilot.app.control.SsidRedactor
import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.ThemeModeStore
import com.wirepilot.app.data.TunnelCatalog
import java.util.concurrent.CopyOnWriteArraySet

class AppContainer(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)
  private val encryptedSsids = EncryptedSsidPreferences.create(appContext)
  private val ssidHmacKey = EncryptedSsidHmacStore(appContext).getOrCreate().also { key ->
    SsidRedactor.installKey(key)
  }
  val store: ControlStore = SharedPreferencesControlStore(preferences)
  val diagnostics: DiagnosticStore = SharedPreferencesDiagnosticStore(preferences)
  val catalog: TunnelCatalog = FileTunnelCatalog(appContext)
  val splitTunnels: SplitTunnelStore = SharedPreferencesSplitTunnelStore(preferences)
  val themeModes: ThemeModeStore = SharedPreferencesThemeModeStore(preferences)
  val excludedSsids: ExcludedSsidStore = SharedPreferencesExcludedSsidStore(encryptedSsids)
  val goBackend = GoBackend(appContext)
  val inventory = NetworkInventory()
  val ssidReader = SsidReader(
    inventory = inventory,
    connectivityManager = appContext.getSystemService(android.net.ConnectivityManager::class.java),
    lastKnown = LastKnownSsid(
      store = SharedPreferencesLastKnownSsidStore(encryptedSsids),
      clock = { System.currentTimeMillis() },
    ),
  )
  val alarms = AlarmScheduler(appContext)
  private val diagnosticLogger = DiagnosticLogger(diagnostics) { System.currentTimeMillis() }
  val logger: DiagnosticLog = DiagnosticLog { kind, detail ->
    diagnosticLogger.record(kind, detail)
  }
  val tunnel = GoTunnelController(goBackend, catalog, splitTunnels, logger)
  val appLockStore = EncryptedAppLockStore(appContext)
  val appLockSession = AppLockSession(appLockStore, clock = { System.currentTimeMillis() })

  init {
    SsidEncryptionMigration.run(preferences, encryptedSsids)
    ExcludedSsidMigration.run(preferences, catalog, excludedSsids)
  }

  val applyRunner = ApplyRunner(
    store = store,
    clock = { System.currentTimeMillis() },
    network = { ssidReader.snapshot() },
    tunnel = tunnel,
    log = logger,
    excludedSsidsFor = { name -> excludedSsids.read(name) },
  )
  val debouncer = ReceiverDebouncer(
    alarms = alarms,
    clock = { System.currentTimeMillis() },
    scheduleStore = SharedPreferencesDebounceScheduleStore(preferences),
    log = logger,
  )
  private val networkUiListeners = CopyOnWriteArraySet<() -> Unit>()
  val networkWatcher = NetworkWatcher(appContext, inventory) {
    val snapshot = ssidReader.snapshot()
    logger.record(
      LogKind.NETWORK_CHANGE,
      LogFormatter.networkChangeDetail(snapshot) +
        " source=callback inventory=${inventory.links().size}",
    )
    debouncer.scheduleDebouncedApply()
    networkUiListeners.forEach { listener -> listener() }
  }.also { watcher ->
    watcher.startLive()
  }

  fun addNetworkUiListener(listener: () -> Unit) {
    networkUiListeners.add(listener)
  }

  fun removeNetworkUiListener(listener: () -> Unit) {
    networkUiListeners.remove(listener)
  }

  fun restartLiveIfSsidUnreadable() {
    val snapshot = ssidReader.snapshot()
    if (snapshot.connectedToWifi && snapshot.wifiSsids.isEmpty()) {
      networkWatcher.restartLive()
    }
  }
  private val networkMonitorRuntime = NetworkMonitorRuntime(
    registerFallbacks = { networkWatcher.registerFallbacks() },
    unregisterFallbacks = { networkWatcher.unregisterFallbacks() },
    startService = { runCatching { NetworkMonitorService.start(appContext) } },
    stopService = { NetworkMonitorService.stop(appContext) },
    whenTunnelIdle = { action -> tunnel.runWhenIdle(action) },
  )
  val networkMonitorCoordinator = NetworkMonitorCoordinator(
    store = store,
    clock = { System.currentTimeMillis() },
    applyMode = { mode, allowServiceStart -> networkMonitorRuntime.apply(mode, allowServiceStart) },
  )
  private val pauseAlarms = alarms.pausePort()
  val homeController = HomeController(
    store = store,
    clock = { System.currentTimeMillis() },
    applyRunner = applyRunner,
    pauseAlarms = pauseAlarms,
    network = { ssidReader.snapshot() },
    diagnostics = diagnostics,
    log = logger,
    catalog = catalog,
    splitTunnels = splitTunnels,
    excludedSsids = excludedSsids,
    tunnelState = tunnel,
    tunnelStats = tunnel,
    ssidMigration = { ExcludedSsidMigration.run(preferences, catalog, excludedSsids) },
    reconcileNetworkMonitor = { networkMonitorCoordinator.reconcile() },
  )
  val pauseRescheduler = PauseRescheduler(
    store = store,
    clock = { System.currentTimeMillis() },
    pauseAlarms = pauseAlarms,
  )
  val bootCoordinator = BootCoordinator(
    reconcileNetworkMonitor = { networkMonitorCoordinator.reconcile() },
    reschedulePause = {
      val scheduled = pauseRescheduler.rescheduleIfNeeded()
      if (scheduled) {
        logger.record(LogKind.PAUSE_RESCHEDULE, "active pause restored")
      }
    },
    scheduleDebouncedApply = { debouncer.scheduleDebouncedApply() },
  )
  val networkChangeCoordinator = NetworkChangeCoordinator(
    scheduleDebouncedApply = { debouncer.scheduleDebouncedApply() },
  )
  val pauseExpiryCoordinator = PauseExpiryCoordinator(
    applyRunner,
    onApplied = { networkMonitorCoordinator.reconcileWithoutServiceStart() },
  )

  fun runDebouncedApply(trigger: String, onSettled: () -> Unit = {}): () -> Unit {
    debouncer.clearArmed()
    applyRunner.applyNow(trigger)
    return tunnel.runWhenIdle(onSettled)
  }
}
