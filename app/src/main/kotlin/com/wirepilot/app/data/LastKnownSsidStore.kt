package com.wirepilot.app.data

data class StoredLastKnownSsid(
  val ssid: String,
  val atMillis: Long = 0L,
)

interface LastKnownSsidStore {
  fun read(): StoredLastKnownSsid?
  fun write(value: StoredLastKnownSsid)
}

object LastKnownSsidCodec {
  fun encode(value: StoredLastKnownSsid?): String {
    if (value == null || value.ssid.isBlank()) {
      return ""
    }
    return "${value.atMillis}\t${value.ssid}"
  }

  fun decode(raw: String?): StoredLastKnownSsid? {
    if (raw.isNullOrBlank()) {
      return null
    }
    val parts = raw.split('\t', limit = 2)
    if (parts.size < 2) {
      val ssid = parts[0].trim()
      return if (ssid.isBlank()) null else StoredLastKnownSsid(ssid = ssid)
    }
    val atMillis = parts[0].toLongOrNull() ?: 0L
    val ssid = parts[1].trim()
    if (ssid.isBlank()) {
      return null
    }
    return StoredLastKnownSsid(ssid = ssid, atMillis = atMillis)
  }
}
