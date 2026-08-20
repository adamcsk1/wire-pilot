package com.wirepilot.app.data

import com.wirepilot.app.control.SsidNormalizer

object ControlCodec {
  private const val SSID_SEPARATOR = '\u001E'

  fun encodeSsids(ssids: Set<String>): String {
    return ssids.sorted().joinToString(SSID_SEPARATOR.toString())
  }

  fun decodeSsids(raw: String?): Set<String> {
    if (raw.isNullOrEmpty()) {
      return emptySet()
    }
    return raw.split(SSID_SEPARATOR)
      .mapNotNull(SsidNormalizer::normalize)
      .toSet()
  }
}
