package com.wirepilot.app.control

sealed class PolicyDecision {
  data class Skip(val reason: SkipReason) : PolicyDecision()
  data class Apply(
    val command: TunnelCommand,
    val tunnelName: String,
  ) : PolicyDecision()
}
