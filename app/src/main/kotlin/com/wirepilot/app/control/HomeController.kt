package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticLogBuffer
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.EmptyExcludedSsidStore
import com.wirepilot.app.data.EmptySplitTunnelStore
import com.wirepilot.app.data.EmptyTunnelCatalog
import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog

class HomeController(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val applyRunner: ApplyRunner,
  private val pauseAlarms: PauseAlarmPort,
  private val network: () -> NetworkSnapshot,
  private val diagnostics: DiagnosticStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
  private val catalog: TunnelCatalog = EmptyTunnelCatalog,
  private val splitTunnels: SplitTunnelStore = EmptySplitTunnelStore,
  private val excludedSsids: ExcludedSsidStore = EmptyExcludedSsidStore,
  private val tunnelState: TunnelStatePort = NoOpTunnelState,
  private val tunnelStats: TunnelStatsPort = NoOpTunnelStats,
  private val ssidMigration: () -> Unit = {},
) {
  private val resolver = ControlResolver(store, clock)
  private val inventory = TunnelInventory(
    store = store,
    applyRunner = applyRunner,
    pauseAlarms = pauseAlarms,
    network = network,
    catalog = catalog,
    splitTunnels = splitTunnels,
    excludedSsids = excludedSsids,
    tunnelState = tunnelState,
    ssidMigration = ssidMigration,
    resolver = resolver,
  )
  private val splitSsids = SplitSsidCommands(
    store = store,
    applyRunner = applyRunner,
    splitTunnels = splitTunnels,
    excludedSsids = excludedSsids,
    catalog = catalog,
    resolver = resolver,
  )

  fun viewState(): HomeViewState {
    val resolved = resolver.persistResolved()
    val selectedSsids = excludedSsids.read(resolved.tunnelName)
    val decision = PolicyEvaluator.decide(resolved.copy(excludedSsids = selectedSsids), network())
    val diagnosticState = diagnostics.read()
    val status = StatusPresenter.present(resolved, clock())
    val names = catalog.names()
    val split = splitTunnels.read(resolved.tunnelName)
    return HomeViewState(
      tunnelName = resolved.tunnelName,
      importedTunnels = names,
      tunnelRows = names.map { name ->
        val rowSplit = splitTunnels.read(name)
        TunnelRow(
          name = name,
          selected = name == resolved.tunnelName,
          up = tunnelState.isUp(name),
          splitMode = rowSplit.mode,
          splitAppCount = rowSplit.packages.size,
          excludedSsidCount = excludedSsids.read(name).size,
          mobile = name == resolved.mobileTunnelName && resolved.mobileTunnelName.isNotBlank(),
        )
      },
      splitTunnelMode = split.mode,
      splitTunnelPackages = split.packages,
      excludedSsids = selectedSsids.sorted(),
      status = status,
      policyLine = PolicyLinePresenter.present(status, decision, network(), selectedSsids),
      applyNow = ApplyNowPresenter.present(decision),
      loggingEnabled = diagnosticState.policyEnabled,
      policyLoggingEnabled = diagnosticState.policyEnabled,
      vpnLoggingEnabled = diagnosticState.vpnEnabled,
      usageEnabled = diagnosticState.usageEnabled,
      logPreview = LogFormatter.preview(diagnosticState.policyEntries, LOG_PREVIEW_LIMIT),
      policyLogText = LogFormatter.formatAll(diagnosticState.policyEntries),
      vpnLogText = LogFormatter.formatAll(diagnosticState.vpnEntries),
      logCopyText = listOf(
        LogFormatter.formatAll(diagnosticState.policyEntries),
        LogFormatter.formatAll(diagnosticState.vpnEntries),
      ).filter { it.isNotBlank() }.joinToString("\n"),
      connectOnMobile = resolved.mobileTunnelName.isNotBlank(),
      mobileTunnelName = resolved.mobileTunnelName,
      controlSelection = ControlSelectionPresenter.present(status),
      vpnConnected = listOf(resolved.tunnelName, resolved.mobileTunnelName)
        .any { name -> name.isNotBlank() && tunnelState.isUp(name) },
    )
  }

  fun setTunnelName(name: String) = inventory.setTunnelName(name)

  fun selectImportedTunnel(name: String) = inventory.selectImportedTunnel(name)

  fun saveTunnel(name: String, conf: String, previousName: String? = null): TunnelSaveResult {
    return inventory.saveTunnel(name, conf, previousName)
  }

  fun reloadImported(imported: List<String>) = inventory.reloadImported(imported)

  fun deleteImportedTunnel(name: String) = inventory.deleteImportedTunnel(name)

  fun splitSettings(tunnelName: String): StoredSplitTunnel = splitSsids.splitSettings(tunnelName)

  fun setSplitTunnel(mode: SplitTunnelMode, packages: Set<String>, tunnelName: String = resolver.persistResolved().tunnelName) {
    splitSsids.setSplitTunnel(mode, packages, tunnelName)
  }

  fun excludedSsids(tunnelName: String): Set<String> = splitSsids.excludedSsids(tunnelName)

  fun addExcludedSsid(raw: String, tunnelName: String = resolver.persistResolved().tunnelName): Boolean {
    return splitSsids.addExcludedSsid(raw, tunnelName)
  }

  fun removeExcludedSsid(raw: String, tunnelName: String = resolver.persistResolved().tunnelName) {
    splitSsids.removeExcludedSsid(raw, tunnelName)
  }

  fun enableControl() {
    store.write(PauseCalculator.resume(resolver.persistResolved()))
    pauseAlarms.cancel()
    log.record(LogKind.RESUME, "control on")
  }

  fun disableControlForever() {
    store.write(PauseCalculator.apply(resolver.persistResolved(), PauseOption.ALWAYS, clock()))
    pauseAlarms.cancel()
    log.record(LogKind.DISABLE, "always")
    applyRunner.applyNow("disable")
  }

  fun pauseFor(option: PauseOption) {
    val next = PauseCalculator.apply(resolver.persistResolved(), option, clock())
    store.write(next)
    val pauseUntil = next.pausedUntilEpochMillis
    if (pauseUntil != null) {
      pauseAlarms.schedule(pauseUntil)
    } else {
      pauseAlarms.cancel()
    }
    log.record(LogKind.PAUSE, option.name)
    applyRunner.applyNow("pause")
  }

  fun setConnectOnMobile(enabled: Boolean, tunnelName: String = resolver.persistResolved().tunnelName) {
    splitSsids.setConnectOnMobile(enabled, tunnelName)
  }

  fun applyNow() {
    applyRunner.applyNow("apply-now")
  }

  fun connectManually() {
    val current = resolver.persistResolved()
    if (current.enabled) {
      return
    }
    val target = manualTarget(current)
    if (target.isBlank()) {
      return
    }
    applyRunner.force(TunnelCommand.UP, "manual-up", target)
  }

  fun disconnectManually() {
    val current = resolver.persistResolved()
    if (current.enabled) {
      return
    }
    if (current.tunnelName.isNotBlank()) {
      applyRunner.force(TunnelCommand.DOWN, "manual-down", current.tunnelName)
    }
    if (current.mobileTunnelName.isNotBlank() && current.mobileTunnelName != current.tunnelName) {
      applyRunner.force(TunnelCommand.DOWN, "manual-down", current.mobileTunnelName)
    }
  }

  fun setLoggingEnabled(enabled: Boolean) {
    setPolicyLoggingEnabled(enabled)
  }

  fun setPolicyLoggingEnabled(enabled: Boolean) {
    diagnostics.write(DiagnosticLogBuffer.setPolicyEnabled(diagnostics.read(), enabled))
  }

  fun setVpnLoggingEnabled(enabled: Boolean) {
    diagnostics.write(DiagnosticLogBuffer.setVpnEnabled(diagnostics.read(), enabled))
  }

  fun setUsageEnabled(enabled: Boolean) {
    diagnostics.write(DiagnosticLogBuffer.setUsageEnabled(diagnostics.read(), enabled))
  }

  fun usageSnapshot(): UsageSnapshot {
    val diagnosticState = diagnostics.read()
    if (!diagnosticState.usageEnabled) {
      return UsageSnapshot(enabled = false)
    }
    val current = store.read()
    val names = listOf(current.tunnelName, current.mobileTunnelName)
      .filter { name -> name.isNotBlank() }
      .distinct()
    val upName = names.firstOrNull { name -> tunnelState.isUp(name) }
      ?: return UsageSnapshot(enabled = true, connected = false)
    val traffic = tunnelStats.traffic(upName)
    return UsageSnapshot(
      enabled = true,
      connected = true,
      tunnelName = upName,
      rxBytes = traffic?.rxBytes ?: 0L,
      txBytes = traffic?.txBytes ?: 0L,
    )
  }

  fun clearLogs() {
    diagnostics.write(DiagnosticLogBuffer.clear(diagnostics.read()))
  }

  fun clearPolicyLogs() {
    diagnostics.write(DiagnosticLogBuffer.clearPolicy(diagnostics.read()))
  }

  fun clearVpnLogs() {
    diagnostics.write(DiagnosticLogBuffer.clearVpn(diagnostics.read()))
  }

  companion object {
    const val LOG_PREVIEW_LIMIT = 80
  }

  private fun manualTarget(current: StoredControl): String {
    val snapshot = network()
    val onMobile = snapshot.kind == NetworkKind.MOBILE || snapshot.kind == NetworkKind.OTHER
    return if (onMobile && current.mobileTunnelName.isNotBlank()) {
      current.mobileTunnelName
    } else {
      current.tunnelName
    }
  }
}
