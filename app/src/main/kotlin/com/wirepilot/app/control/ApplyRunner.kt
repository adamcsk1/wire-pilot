package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore

class ApplyRunner(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val network: () -> NetworkSnapshot,
  private val tunnel: TunnelCommands,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
) {
  fun applyNow(trigger: String = "apply"): Boolean {
    val nowMillis = clock()
    val stored = store.read()
    val resolved = ControlModeResolver.resolve(stored, nowMillis)
    if (resolved != stored) {
      store.write(resolved)
    }
    val snapshot = network()
    val attempt = UnreadableRetryPolicy.attemptNumber(trigger)
    val decision = PolicyEvaluator.decide(resolved, snapshot)
    val kind = when {
      trigger == "apply-now" -> LogKind.APPLY_NOW
      trigger == DebounceTriggers.DEBOUNCE ||
        trigger == DebounceTriggers.PROCESS_START ||
        UnreadableRetryPolicy.isRetryTrigger(trigger) -> LogKind.DEBOUNCE
      else -> LogKind.APPLY
    }
    log.record(
      kind,
      LogFormatter.applyDetail(
        trigger = trigger,
        control = resolved,
        network = snapshot,
        decision = decision,
        attempt = attempt,
        maxAttempts = UnreadableRetryPolicy.MAX_ATTEMPTS,
      ),
    )
    when (decision) {
      is PolicyDecision.Skip -> Unit
      is PolicyDecision.Apply -> tunnel.send(resolved.tunnelName, decision.command)
    }
    return UnreadableRetryPolicy.shouldRetry(trigger, decision)
  }
}
