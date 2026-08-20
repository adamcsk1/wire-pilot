package com.wirepilot.app.control

object ConfigZipLimits {
  const val MAX_ENTRIES = 32
  const val MAX_ENTRY_BYTES = 64 * 1024
  const val MAX_TOTAL_BYTES = 512 * 1024

  fun acceptEntry(entryIndex: Int, entryBytes: Int, totalBytes: Int): Boolean {
    return entryIndex < MAX_ENTRIES &&
      entryBytes in 1..MAX_ENTRY_BYTES &&
      totalBytes <= MAX_TOTAL_BYTES
  }
}
