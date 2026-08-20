package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.DiagnosticLogBuffer
import com.wirepilot.app.data.DiagnosticStore
import com.wirepilot.app.data.StoredControl

class HomeController(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val applyRunner: ApplyRunner,
  private val pauseAlarms: PauseAlarmPort,
  private val network: () -> NetworkSnapshot,
  private val diagnostics: DiagnosticStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
  private val watching: WatchingServicePort = NoOpWatchingService,
) {
  fun viewState(): HomeViewState {
    val resolved = persistResolved()
    val decision = PolicyEvaluator.decide(resolved, network())
    val diagnosticState = diagnostics.read()
    val status = StatusPresenter.present(resolved, clock())
    return HomeViewState(
      tunnelName = resolved.tunnelName,
      excludedSsids = resolved.excludedSsids.sorted(),
      status = status,
      applyNow = ApplyNowPresenter.present(decision),
      loggingEnabled = diagnosticState.enabled,
      logPreview = LogFormatter.preview(diagnosticState.entries, LOG_PREVIEW_LIMIT),
      logCopyText = LogFormatter.formatAll(diagnosticState.entries),
      connectOnMobile = resolved.connectOnMobile,
      controlSelection = ControlSelectionPresenter.present(status),
    )
  }

  fun setTunnelName(name: String) {
    val current = persistResolved()
    store.write(current.copy(tunnelName = name.trim()))
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
  }

  fun setConnectOnMobile(enabled: Boolean) {
    store.write(persistResolved().copy(connectOnMobile = enabled))
    applyRunner.applyNow("mobile-flag")
  }

  fun applyNow() {
    applyRunner.applyNow("apply-now")
  }

  fun setLoggingEnabled(enabled: Boolean) {
    diagnostics.write(DiagnosticLogBuffer.setEnabled(diagnostics.read(), enabled))
  }

  fun clearLogs() {
    diagnostics.write(DiagnosticLogBuffer.clear(diagnostics.read()))
  }

  companion object {
    const val LOG_PREVIEW_LIMIT = 80
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
