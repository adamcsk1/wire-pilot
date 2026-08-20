package com.wirepilot.app.control

object SsidNormalizer {
  private const val UNKNOWN_SSID = "<unknown ssid>"

  fun normalize(raw: String?): String? {
    if (raw.isNullOrBlank()) {
      return null
    }
    val trimmed = raw.trim()
    val unquoted = stripWrappingQuotes(trimmed)
    if (unquoted.isBlank()) {
      return null
    }
    if (unquoted.equals(UNKNOWN_SSID, ignoreCase = true) ||
      unquoted.equals("unknown ssid", ignoreCase = true)
    ) {
      return null
    }
    return unquoted
  }

  private fun stripWrappingQuotes(value: String): String {
    if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
      return value.substring(1, value.length - 1).trim()
    }
    return value
  }
}
