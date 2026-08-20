package com.wirepilot.app.control

object UnreadableRetryPolicy {
  const val RETRY_TRIGGER_PREFIX = "unreadable-retry-"
  const val MAX_ATTEMPTS = 5

  fun isRetryTrigger(trigger: String): Boolean {
    return trigger.startsWith(RETRY_TRIGGER_PREFIX)
  }

  fun attemptNumber(trigger: String): Int {
    if (!isRetryTrigger(trigger)) {
      return 1
    }
    return trigger.removePrefix(RETRY_TRIGGER_PREFIX).toIntOrNull() ?: MAX_ATTEMPTS
  }

  fun nextTrigger(trigger: String): String {
    return "$RETRY_TRIGGER_PREFIX${attemptNumber(trigger) + 1}"
  }

  fun shouldRetry(trigger: String, decision: PolicyDecision): Boolean {
    val isSettleTrigger = trigger == DebounceTriggers.DEBOUNCE ||
      trigger == DebounceTriggers.PROCESS_START ||
      isRetryTrigger(trigger)
    if (!isSettleTrigger) {
      return false
    }
    if (decision !is PolicyDecision.Skip || decision.reason != SkipReason.WIFI_SSID_UNREADABLE) {
      return false
    }
    return attemptNumber(trigger) < MAX_ATTEMPTS
  }
}
