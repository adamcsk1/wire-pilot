package com.wirepilot.app.data

import com.wirepilot.app.control.LogEvent
import com.wirepilot.app.control.LogKind

object DiagnosticCodec {
  fun encode(state: DiagnosticState): String {
    val header = if (state.enabled) "1" else "0"
    if (state.entries.isEmpty()) {
      return header
    }
    val body = state.entries.joinToString("\n") { event ->
      "${event.atMillis}\t${event.kind.name}\t${sanitize(event.detail)}"
    }
    return "$header\n$body"
  }

  fun decode(raw: String?): DiagnosticState {
    if (raw.isNullOrEmpty()) {
      return DiagnosticState()
    }
    val lines = raw.split('\n')
    val enabled = lines.first() != "0"
    val entries = lines.drop(1).mapNotNull(::decodeEvent)
    return DiagnosticState(enabled = enabled, entries = entries)
  }

  private fun decodeEvent(line: String): LogEvent? {
    if (line.isBlank()) {
      return null
    }
    val parts = line.split('\t', limit = 3)
    if (parts.size < 2) {
      return null
    }
    val atMillis = parts[0].toLongOrNull() ?: return null
    val kind = runCatching { LogKind.valueOf(parts[1]) }.getOrNull() ?: return null
    val detail = parts.getOrElse(2) { "" }
    return LogEvent(atMillis = atMillis, kind = kind, detail = detail)
  }

  private fun sanitize(detail: String): String {
    return detail.replace('\n', ' ').replace('\t', ' ')
  }
}
