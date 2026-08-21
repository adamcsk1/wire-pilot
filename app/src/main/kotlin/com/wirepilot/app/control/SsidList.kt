package com.wirepilot.app.control

import com.wirepilot.app.data.SsidNormalizer

object SsidList {
  fun add(current: Set<String>, raw: String): Set<String> {
    val ssid = SsidNormalizer.normalize(raw) ?: return current
    return current + ssid
  }

  fun remove(current: Set<String>, raw: String): Set<String> {
    val ssid = SsidNormalizer.normalize(raw) ?: return current
    return current - ssid
  }
}
