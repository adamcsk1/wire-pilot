package com.wirepilot.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.wirepilot.app.control.ApplyRunner
import com.wirepilot.app.control.BootCoordinator
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.DiagnosticLogger
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.LogFormatter
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.control.NetworkChangeCoordinator
import com.wirepilot.app.control.PauseExpiryCoordinator
import com.wirepilot.app.control.PauseRescheduler
import com.wirepilot.app.control.StatusPresenter
import com.wirepilot.app.control.UnreadableRetryPolicy
import com.wirepilot.app.control.WatchingPolicy
import com.wirepilot.app.control.WatchingServicePort
import com.wireguard.android.backend.GoBackend
import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.TunnelCatalog

class AppContainer(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)
  val store: ControlStore = SharedPreferencesControlStore(preferences)
  val diagnostics: DiagnosticStore = SharedPreferencesDiagnosticStore(preferences)
  val catalog: TunnelCatalog = FileTunnelCatalog(appContext)
  val splitTunnels: SplitTunnelStore = SharedPreferencesSplitTunnelStore(preferences)
  val goBackend = GoBackend(appContext)
  val inventory = NetworkInventory()
  val ssidReader = SsidReader(
    inventory = inventory,
    connectivityManager = appContext.getSystemService(android.net.ConnectivityManager::class.java),
    wifiManager = appContext.getSystemService(android.net.wifi.WifiManager::class.java),
    readiness = { SsidReadinessReader.read(appContext) },
    lastKnown = LastKnownSsid(
      store = SharedPreferencesLastKnownSsidStore(preferences),
      clock = { System.currentTimeMillis() },
    ),
  )
  val alarms = AlarmScheduler(appContext)
  private val diagnosticLogger = DiagnosticLogger(diagnostics) { System.currentTimeMillis() }
  val logger: DiagnosticLog = DiagnosticLog { kind, detail ->
    diagnosticLogger.record(kind, detail)
  }
  val applyRunner = ApplyRunner(
    store = store,
    clock = { System.currentTimeMillis() },
    network = { ssidReader.snapshot() },
    tunnel = GoTunnelController(goBackend, catalog, splitTunnels, logger),
    log = logger,
  )
  val debouncer = ReceiverDebouncer(
    alarms = alarms,
    clock = { System.currentTimeMillis() },
    scheduleStore = SharedPreferencesDebounceScheduleStore(preferences),
    log = logger,
    apply = { trigger -> runDebouncedApply(trigger) },
  )
  val networkWatcher = NetworkWatcher(appContext, inventory) {
    val snapshot = ssidReader.snapshot()
    logger.record(
      LogKind.NETWORK_CHANGE,
      LogFormatter.networkChangeDetail(snapshot) +
        " source=callback inventory=${inventory.links().size}",
    )
    debouncer.scheduleDebouncedApply()
  }
  private val pauseAlarms = alarms.pausePort()
  val watching: WatchingServicePort = WatchingServiceController(
    context = appContext,
    log = logger,
    notificationsGranted = {
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    },
  )
  val homeController = HomeController(
    store = store,
    clock = { System.currentTimeMillis() },
    applyRunner = applyRunner,
    pauseAlarms = pauseAlarms,
    network = { ssidReader.snapshot() },
    diagnostics = diagnostics,
    log = logger,
    watching = watching,
    catalog = catalog,
    splitTunnels = splitTunnels,
  )
  val pauseRescheduler = PauseRescheduler(
    store = store,
    clock = { System.currentTimeMillis() },
    pauseAlarms = pauseAlarms,
  )
  val bootCoordinator = BootCoordinator(
    registerNetworkWatcher = {
      networkWatcher.register()
      logger.record(LogKind.NETWORK_CHANGE, "watcher registered")
    },
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
  val pauseExpiryCoordinator = PauseExpiryCoordinator(applyRunner) {
    val status = StatusPresenter.present(store.read(), System.currentTimeMillis())
    watching.sync(WatchingPolicy.shouldWatch(status))
  }

  fun runDebouncedApply(trigger: String) {
    debouncer.clearArmed()
    val shouldRetry = applyRunner.applyNow(trigger)
    if (shouldRetry) {
      val nextTrigger = UnreadableRetryPolicy.nextTrigger(trigger)
      logger.record(
        LogKind.DEBOUNCE,
        "scheduling $nextTrigger of ${UnreadableRetryPolicy.MAX_ATTEMPTS}",
      )
      debouncer.scheduleUnreadableRetry(nextTrigger)
    }
  }
}
