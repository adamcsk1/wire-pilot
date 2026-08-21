package com.wirepilot.app.control

import java.util.Locale

object ByteFormatter {
  private val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

  fun format(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) {
      return "$safe B"
    }
    var value = safe.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
      value /= 1024.0
      unit += 1
    }
    val label = units[unit]
    return if (value >= 100.0) {
      "${value.toInt()} $label"
    } else if (value >= 10.0) {
      String.format(Locale.US, "%.1f %s", value, label)
    } else {
      String.format(Locale.US, "%.2f %s", value, label)
    }
  }
}
