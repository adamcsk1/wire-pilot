package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LogFormatter {
  fun format(event: LogEvent, zone: ZoneId = ZoneId.systemDefault()): String {
    val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
    val time = clockFormat.format(Instant.ofEpochMilli(event.atMillis))
    return if (event.detail.isBlank()) {
      "$time ${event.kind.name}"
    } else {
      "$time ${event.kind.name} ${event.detail}"
    }
  }

  fun formatAll(events: List<LogEvent>, zone: ZoneId = ZoneId.systemDefault()): String {
    return events.joinToString("\n") { event -> format(event, zone) }
  }

  fun applyDetail(
    trigger: String,
    control: StoredControl,
    network: NetworkSnapshot,
    decision: PolicyDecision,
    attempt: Int = 1,
    maxAttempts: Int = 1,
  ): String {
    val apply = when (decision) {
      is PolicyDecision.Skip -> "skip/${decision.reason.name}"
      is PolicyDecision.Apply -> "${decision.command.name.lowercase()} via=go-backend"
    }
    val target = when (decision) {
      is PolicyDecision.Apply -> decision.tunnelName
      is PolicyDecision.Skip -> control.tunnelName
    }
    val tunnel = target.ifBlank { "(blank)" }
    return "trigger=$trigger apply=$apply net=${network.kind} ssid=${ssidLabel(network)} tunnel=$tunnel retry=$attempt/$maxAttempts ssidSource=${network.ssidSource}"
  }

  fun networkChangeDetail(network: NetworkSnapshot): String {
    return "net=${network.kind} ssid=${ssidLabel(network)} ssidSource=${network.ssidSource}"
  }

  fun preview(events: List<LogEvent>, limit: Int, zone: ZoneId = ZoneId.systemDefault()): String {
    if (events.isEmpty()) {
      return ""
    }
    val shown = events.takeLast(limit.coerceAtLeast(0))
    val header = "showing last ${shown.size} of ${events.size}"
    val body = formatAll(shown, zone)
    return if (body.isBlank()) header else "$header\n$body"
  }

  private fun ssidLabel(network: NetworkSnapshot): String {
    return when (network.kind) {
      NetworkKind.WIFI -> {
        if (network.wifiSsids.isEmpty()) {
          "?"
        } else {
          network.wifiSsids.sorted().joinToString(",") { ssid -> SsidRedactor.redact(ssid) }
        }
      }
      NetworkKind.WIFI_SETTLING -> "?"
      NetworkKind.MOBILE, NetworkKind.OTHER -> "-"
    }
  }
}
