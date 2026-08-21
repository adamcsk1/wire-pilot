package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticLogBuffer
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.EmptySplitTunnelStore
import com.wirepilot.app.data.EmptyTunnelCatalog
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
  private val tunnelState: TunnelStatePort = NoOpTunnelState,
) {
  fun viewState(): HomeViewState {
    val resolved = persistResolved()
    val decision = PolicyEvaluator.decide(resolved, network())
    val diagnosticState = diagnostics.read()
    val status = StatusPresenter.present(resolved, clock())
    val names = catalog.names()
    val split = splitTunnels.read(resolved.tunnelName)
    return HomeViewState(
      tunnelName = resolved.tunnelName,
      importedTunnels = names,
      tunnelRows = names.map { name ->
        TunnelRow(
          name = name,
          selected = name == resolved.tunnelName,
          up = tunnelState.isUp(name),
        )
      },
      splitTunnelMode = split.mode,
      splitTunnelPackages = split.packages,
      excludedSsids = resolved.excludedSsids.sorted(),
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
      connectOnMobile = resolved.connectOnMobile,
      controlSelection = ControlSelectionPresenter.present(status),
      vpnConnected = resolved.tunnelName.isNotBlank() && tunnelState.isUp(resolved.tunnelName),
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
    val renaming = !previousName.isNullOrBlank() && previousName != trimmed
    val previousWasUp = renaming && tunnelState.isUp(previousName)
    if (renaming) {
      if (previousWasUp) {
        applyRunner.force(TunnelCommand.DOWN, "tunnel-rename", previousName)
      }
      splitTunnels.write(trimmed, splitTunnels.read(previousName))
      splitTunnels.delete(previousName)
      catalog.delete(previousName)
      val current = persistResolved()
      if (current.tunnelName == previousName) {
        store.write(current.copy(tunnelName = trimmed))
      }
    }
    val current = persistResolved()
    if (current.tunnelName.isBlank()) {
      selectImportedTunnel(trimmed)
      return TunnelSaveResult.SAVED
    }
    if (current.tunnelName != trimmed) {
      return TunnelSaveResult.SAVED
    }
    applySavedTunnel(trimmed, "tunnel-save", previousWasUp || tunnelState.isUp(trimmed))
    return TunnelSaveResult.SAVED
  }

  fun reloadImported(imported: List<String>) {
    if (imported.isEmpty()) {
      return
    }
    val current = persistResolved()
    if (current.tunnelName.isBlank()) {
      selectImportedTunnel(imported.first())
      return
    }
    if (current.tunnelName !in imported) {
      return
    }
    applySavedTunnel(current.tunnelName, "tunnel-import", tunnelState.isUp(current.tunnelName))
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
    val current = persistResolved()
    if (current.tunnelName != name) {
      return
    }
    val remaining = catalog.names()
    if (remaining.isEmpty()) {
      store.write(current.copy(tunnelName = "", enabled = false, pausedUntilEpochMillis = null))
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
    if (persistResolved().tunnelName == tunnelName) {
      applyRunner.applyNow("split-tunnel")
    }
  }

  fun addExcludedSsid(raw: String): Boolean {
    val current = persistResolved()
    val nextSsids = SsidList.add(current.excludedSsids, raw)
    if (nextSsids == current.excludedSsids) {
      return false
    }
    store.write(current.copy(excludedSsids = nextSsids))
    applyRunner.applyNow("ssid-add")
    return true
  }

  fun removeExcludedSsid(raw: String) {
    val current = persistResolved()
    store.write(current.copy(excludedSsids = SsidList.remove(current.excludedSsids, raw)))
    applyRunner.applyNow("ssid-remove")
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

  fun setConnectOnMobile(enabled: Boolean) {
    store.write(persistResolved().copy(connectOnMobile = enabled))
    applyRunner.applyNow("mobile-flag")
  }

  fun applyNow() {
    applyRunner.applyNow("apply-now")
  }

  fun connectManually() {
    if (persistResolved().enabled) {
      return
    }
    applyRunner.force(TunnelCommand.UP, "manual-up")
  }

  fun disconnectManually() {
    if (persistResolved().enabled) {
      return
    }
    applyRunner.force(TunnelCommand.DOWN, "manual-down")
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
