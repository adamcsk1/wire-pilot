package com.wirepilot.app.data

object DiagnosticLogBuffer {
  const val MAX_ENTRIES = 150

  fun append(state: DiagnosticState, event: LogEvent): DiagnosticState {
    return when (LogChannels.of(event.kind)) {
      LogChannel.POLICY -> {
        if (!state.policyEnabled) {
          state
        } else {
          state.copy(policyEntries = (state.policyEntries + event).takeLast(MAX_ENTRIES))
        }
      }
      LogChannel.VPN -> {
        if (!state.vpnEnabled) {
          state
        } else {
          state.copy(vpnEntries = (state.vpnEntries + event).takeLast(MAX_ENTRIES))
        }
      }
    }
  }

  fun clearPolicy(state: DiagnosticState): DiagnosticState {
    return state.copy(policyEntries = emptyList())
  }

  fun clearVpn(state: DiagnosticState): DiagnosticState {
    return state.copy(vpnEntries = emptyList())
  }

  fun clear(state: DiagnosticState): DiagnosticState {
    return state.copy(policyEntries = emptyList(), vpnEntries = emptyList())
  }

  fun setPolicyEnabled(state: DiagnosticState, enabled: Boolean): DiagnosticState {
    return state.copy(policyEnabled = enabled)
  }

  fun setVpnEnabled(state: DiagnosticState, enabled: Boolean): DiagnosticState {
    return state.copy(vpnEnabled = enabled)
  }

  fun setEnabled(state: DiagnosticState, enabled: Boolean): DiagnosticState {
    return state.copy(policyEnabled = enabled, vpnEnabled = enabled)
  }

  fun setUsageEnabled(state: DiagnosticState, enabled: Boolean): DiagnosticState {
    return state.copy(usageEnabled = enabled)
  }
}
