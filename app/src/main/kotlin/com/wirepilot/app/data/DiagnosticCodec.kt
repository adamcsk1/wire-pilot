package com.wirepilot.app.data

import com.wirepilot.app.control.LogChannel
import com.wirepilot.app.control.LogEvent
import com.wirepilot.app.control.LogKind

object DiagnosticCodec {
  fun encode(state: DiagnosticState): String {
    val header = "${flag(state.policyEnabled)}\t${flag(state.vpnEnabled)}\t${flag(state.usageEnabled)}"
    val events = (state.policyEntries.map { it to LogChannel.POLICY } +
      state.vpnEntries.map { it to LogChannel.VPN })
      .sortedBy { pair -> pair.first.atMillis }
    if (events.isEmpty()) {
      return header
    }
    val body = events.joinToString("\n") { (event, channel) ->
      "${event.atMillis}\t${event.kind.name}\t${channel.name}\t${sanitize(event.detail)}"
    }
    return "$header\n$body"
  }

  fun decode(raw: String?): DiagnosticState {
    if (raw.isNullOrEmpty()) {
      return DiagnosticState()
    }
    val lines = raw.split('\n')
    val header = lines.first()
    val policyEnabled: Boolean
    val vpnEnabled: Boolean
    val usageEnabled: Boolean
    if (header.contains('\t')) {
      val flags = header.split('\t')
      policyEnabled = flags.getOrNull(0) != "0"
      vpnEnabled = flags.getOrNull(1) != "0"
      usageEnabled = flags.getOrNull(2) == "1"
    } else {
      policyEnabled = header != "0"
      vpnEnabled = true
      usageEnabled = false
    }
    val policy = mutableListOf<LogEvent>()
    val vpn = mutableListOf<LogEvent>()
    lines.drop(1).mapNotNull(::decodeEvent).forEach { (event, channel) ->
      if (channel == LogChannel.VPN) {
        vpn += event
      } else {
        policy += event
      }
    }
    return DiagnosticState(
      policyEnabled = policyEnabled,
      vpnEnabled = vpnEnabled,
      usageEnabled = usageEnabled,
      policyEntries = policy,
      vpnEntries = vpn,
    )
  }

  private fun decodeEvent(line: String): Pair<LogEvent, LogChannel>? {
    if (line.isBlank()) {
      return null
    }
    val parts = line.split('\t', limit = 4)
    if (parts.size < 2) {
      return null
    }
    val atMillis = parts[0].toLongOrNull() ?: return null
    val kind = runCatching { LogKind.valueOf(parts[1]) }.getOrNull() ?: return null
    val channel: LogChannel
    val detail: String
    if (parts.size >= 4 && runCatching { LogChannel.valueOf(parts[2]) }.getOrNull() != null) {
      channel = LogChannel.valueOf(parts[2])
      detail = parts[3]
    } else {
      channel = LogChannel.POLICY
      detail = parts.getOrElse(2) { "" }
    }
    return LogEvent(atMillis = atMillis, kind = kind, detail = detail) to channel
  }

  private fun flag(enabled: Boolean): String {
    return if (enabled) "1" else "0"
  }

  private fun sanitize(detail: String): String {
    return detail.replace('\n', ' ').replace('\t', ' ')
  }
}
