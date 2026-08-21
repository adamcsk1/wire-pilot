package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticLogBuffer
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.EmptyExcludedSsidStore
import com.wirepilot.app.data.EmptySplitTunnelStore
import com.wirepilot.app.data.EmptyTunnelCatalog
import com.wirepilot.app.data.ExcludedSsidStore
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
  private val watching: WatchingServicePort = NoOpWatchingService,
  private val catalog: TunnelCatalog = EmptyTunnelCatalog,
  private val splitTunnels: SplitTunnelStore = EmptySplitTunnelStore,
  private val excludedSsids: ExcludedSsidStore = EmptyExcludedSsidStore,
  private val tunnelState: TunnelStatePort = NoOpTunnelState,
  private val ssidMigration: () -> Unit = {},
) {
  fun viewState(): HomeViewState {
    val resolved = persistResolved()
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
      applyNow = ApplyNowPresenter.present(decision),
      loggingEnabled = diagnosticState.policyEnabled,
      policyLoggingEnabled = diagnosticState.policyEnabled,
      vpnLoggingEnabled = diagnosticState.vpnEnabled,
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

  fun setTunnelName(name: String) {
    val current = persistResolved()
    val trimmed = name.trim()
    if (!ConfigZipNames.isValidTunnelName(trimmed) && trimmed.isNotEmpty()) {
      return
    }
    store.write(current.copy(tunnelName = trimmed))
  }

  fun selectImportedTunnel(name: String) {
    if (name !in catalog.names()) {
      return
    }
    val previous = persistResolved().tunnelName
    setTunnelName(name)
    if (persistResolved().enabled) {
      applyRunner.applyNow("tunnel-select")
    } else if (previous.isNotBlank() && previous != name) {
      applyRunner.force(TunnelCommand.DOWN, "tunnel-switch", previous)
    }
  }

  fun saveTunnel(name: String, conf: String, previousName: String? = null): TunnelSaveResult {
    val trimmed = name.trim()
    if (!ConfigZipNames.isValidTunnelName(trimmed)) {
      return TunnelSaveResult.INVALID_NAME
    }
    if (conf.isBlank()) {
      return TunnelSaveResult.INVALID_CONF
    }
    val names = catalog.names()
    val sameAsPrevious = !previousName.isNullOrBlank() && previousName == trimmed
    if (trimmed in names && !sameAsPrevious) {
      return TunnelSaveResult.NAME_IN_USE
    }
    val wrote = runCatching { catalog.writeConf(trimmed, conf) }.isSuccess
    if (!wrote) {
      return TunnelSaveResult.WRITE_FAILED
    }
    ssidMigration()
    val renaming = !previousName.isNullOrBlank() && previousName != trimmed
    val previousWasUp = renaming && tunnelState.isUp(previousName)
    if (renaming) {
      if (previousWasUp) {
        applyRunner.force(TunnelCommand.DOWN, "tunnel-rename", previousName)
      }
      splitTunnels.write(trimmed, splitTunnels.read(previousName))
      splitTunnels.delete(previousName)
      excludedSsids.write(trimmed, excludedSsids.read(previousName))
      excludedSsids.delete(previousName)
      catalog.delete(previousName)
      val current = persistResolved()
      if (current.tunnelName == previousName || current.mobileTunnelName == previousName) {
        store.write(
          current.copy(
            tunnelName = if (current.tunnelName == previousName) trimmed else current.tunnelName,
            mobileTunnelName = if (current.mobileTunnelName == previousName) trimmed else current.mobileTunnelName,
          ),
        )
      }
    }
    val current = persistResolved()
    if (current.tunnelName.isBlank()) {
      assignMobileIfBlank(trimmed)
      selectImportedTunnel(trimmed)
      return TunnelSaveResult.SAVED
    }
    if (current.tunnelName != trimmed && current.mobileTunnelName != trimmed) {
      return TunnelSaveResult.SAVED
    }
    applySavedTunnel(trimmed, "tunnel-save", previousWasUp || tunnelState.isUp(trimmed))
    return TunnelSaveResult.SAVED
  }

  fun reloadImported(imported: List<String>) {
    if (imported.isEmpty()) {
      return
    }
    ssidMigration()
    val current = persistResolved()
    if (current.tunnelName.isBlank()) {
      assignMobileIfBlank(imported.first())
      selectImportedTunnel(imported.first())
      return
    }
    val importedMobile = current.mobileTunnelName.isNotBlank() && current.mobileTunnelName in imported
    if (current.tunnelName !in imported && !importedMobile) {
      return
    }
    val liveName = if (importedMobile && tunnelState.isUp(current.mobileTunnelName)) {
      current.mobileTunnelName
    } else {
      current.tunnelName
    }
    applySavedTunnel(liveName, "tunnel-import", tunnelState.isUp(liveName))
  }

  fun deleteImportedTunnel(name: String) {
    if (name !in catalog.names()) {
      return
    }
    if (tunnelState.isUp(name)) {
      applyRunner.force(TunnelCommand.DOWN, "tunnel-delete", name)
    }
    catalog.delete(name)
    splitTunnels.delete(name)
    excludedSsids.delete(name)
    val current = persistResolved()
    val clearedMobile = if (current.mobileTunnelName == name) {
      current.copy(mobileTunnelName = "")
    } else {
      current
    }
    if (clearedMobile != current) {
      store.write(clearedMobile)
    }
    if (clearedMobile.tunnelName != name) {
      return
    }
    val remaining = catalog.names()
    if (remaining.isEmpty()) {
      store.write(clearedMobile.copy(tunnelName = "", enabled = false, pausedUntilEpochMillis = null))
      pauseAlarms.cancel()
      syncWatching()
      return
    }
    selectImportedTunnel(remaining.first())
  }

  fun splitSettings(tunnelName: String): StoredSplitTunnel {
    return splitTunnels.read(tunnelName)
  }

  fun setSplitTunnel(mode: SplitTunnelMode, packages: Set<String>, tunnelName: String = persistResolved().tunnelName) {
    if (tunnelName.isBlank()) {
      return
    }
    val selection = SplitTunnelPolicy.selection(mode, packages)
    val storedMode = SplitTunnelPolicy.modeFrom(selection.excludedPackages, selection.includedPackages)
    val storedPackages = selection.excludedPackages + selection.includedPackages
    splitTunnels.write(tunnelName, StoredSplitTunnel(storedMode, storedPackages))
    val resolved = persistResolved()
    if (resolved.tunnelName == tunnelName || resolved.mobileTunnelName == tunnelName) {
      applyRunner.applyNow("split-tunnel")
    }
  }

  fun excludedSsids(tunnelName: String): Set<String> {
    return excludedSsids.read(tunnelName)
  }

  fun addExcludedSsid(raw: String, tunnelName: String = persistResolved().tunnelName): Boolean {
    if (tunnelName.isBlank()) {
      return false
    }
    val current = excludedSsids.read(tunnelName)
    val nextSsids = SsidList.add(current, raw)
    if (nextSsids == current) {
      return false
    }
    excludedSsids.write(tunnelName, nextSsids)
    if (persistResolved().tunnelName == tunnelName) {
      applyRunner.applyNow("ssid-add")
    }
    return true
  }

  fun removeExcludedSsid(raw: String, tunnelName: String = persistResolved().tunnelName) {
    if (tunnelName.isBlank()) {
      return
    }
    excludedSsids.write(tunnelName, SsidList.remove(excludedSsids.read(tunnelName), raw))
    if (persistResolved().tunnelName == tunnelName) {
      applyRunner.applyNow("ssid-remove")
    }
  }

  fun enableControl() {
    store.write(PauseCalculator.resume(persistResolved()))
    pauseAlarms.cancel()
    log.record(LogKind.RESUME, "control on")
    syncWatching()
    applyRunner.applyNow("enable")
  }

  fun disableControlForever() {
    store.write(PauseCalculator.apply(persistResolved(), PauseOption.ALWAYS, clock()))
    pauseAlarms.cancel()
    log.record(LogKind.DISABLE, "always")
    syncWatching()
    applyRunner.applyNow("disable")
  }

  fun pauseFor(option: PauseOption) {
    val next = PauseCalculator.apply(persistResolved(), option, clock())
    store.write(next)
    val pauseUntil = next.pausedUntilEpochMillis
    if (pauseUntil != null) {
      pauseAlarms.schedule(pauseUntil)
    } else {
      pauseAlarms.cancel()
    }
    log.record(LogKind.PAUSE, option.name)
    syncWatching()
    applyRunner.applyNow("pause")
  }

  fun setConnectOnMobile(enabled: Boolean, tunnelName: String = persistResolved().tunnelName) {
    if (tunnelName.isBlank()) {
      return
    }
    val names = catalog.names()
    if (names.isNotEmpty() && tunnelName !in names) {
      return
    }
    val current = persistResolved()
    val nextMobile = if (enabled) {
      tunnelName
    } else if (current.mobileTunnelName == tunnelName) {
      ""
    } else {
      current.mobileTunnelName
    }
    store.write(current.copy(mobileTunnelName = nextMobile))
    applyRunner.applyNow("mobile-flag")
  }

  fun applyNow() {
    applyRunner.applyNow("apply-now")
  }

  fun connectManually() {
    val current = persistResolved()
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
    val current = persistResolved()
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

  private fun assignMobileIfBlank(name: String) {
    val current = persistResolved()
    if (current.mobileTunnelName.isNotBlank() || name !in catalog.names()) {
      return
    }
    store.write(current.copy(mobileTunnelName = name))
  }

  private fun applySavedTunnel(tunnelName: String, trigger: String, keepUp: Boolean) {
    val resolved = persistResolved()
    if (resolved.enabled) {
      applyRunner.applyNow(trigger)
      if (keepUp && PolicyEvaluator.decide(resolved, network()) is PolicyDecision.Skip) {
        applyRunner.force(TunnelCommand.UP, trigger, tunnelName)
      }
    } else if (keepUp) {
      applyRunner.force(TunnelCommand.UP, trigger, tunnelName)
    }
  }

  private fun persistResolved(): StoredControl {
    val stored = store.read()
    val resolved = ControlModeResolver.resolve(stored, clock())
    if (resolved != stored) {
      store.write(resolved)
    }
    return resolved
  }

  private fun syncWatching() {
    val status = StatusPresenter.present(store.read(), clock())
    watching.sync(WatchingPolicy.shouldWatch(status))
  }
}
