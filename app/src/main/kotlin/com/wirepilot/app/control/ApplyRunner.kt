package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.StoredControl

class ApplyRunner(
  private val store: ControlStore,
  private val clock: () -> Long,
  private val network: () -> NetworkSnapshot,
  private val tunnel: TunnelCommands,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
  private val excludedSsidsFor: (String) -> Set<String>? = { null },
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
    val forPolicy = resolved.copy(
      excludedSsids = excludedSsidsFor(resolved.tunnelName) ?: resolved.excludedSsids,
    )
    val decision = PolicyEvaluator.decide(forPolicy, snapshot)
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
        control = forPolicy,
        network = snapshot,
        decision = decision,
        attempt = attempt,
        maxAttempts = UnreadableRetryPolicy.MAX_ATTEMPTS,
      ),
    )
    when (decision) {
      is PolicyDecision.Skip -> Unit
      is PolicyDecision.Apply -> {
        tunnel.send(decision.tunnelName, decision.command)
        if (decision.command == TunnelCommand.DOWN) {
          downCompanion(resolved, decision.tunnelName)
        }
      }
    }
    return UnreadableRetryPolicy.shouldRetry(trigger, decision)
  }

  fun force(command: TunnelCommand, trigger: String, tunnelName: String = store.read().tunnelName) {
    if (tunnelName.isBlank()) {
      return
    }
    log.record(LogKind.TUNNEL, "trigger=$trigger command=${command.name.lowercase()} tunnel=$tunnelName")
    tunnel.send(tunnelName, command)
  }

  private fun downCompanion(control: StoredControl, alreadyDown: String) {
    val other = when (alreadyDown) {
      control.tunnelName -> control.mobileTunnelName
      else -> control.tunnelName
    }.trim()
    if (other.isNotBlank() && other != alreadyDown) {
      tunnel.send(other, TunnelCommand.DOWN)
    }
  }
}
