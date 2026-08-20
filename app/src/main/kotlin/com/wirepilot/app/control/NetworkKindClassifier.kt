package com.wirepilot.app.control

object NetworkKindClassifier {
  fun classify(
    hasReadableSsid: Boolean,
    hasAnyWifi: Boolean,
    hasCellular: Boolean,
  ): NetworkKind {
    if (hasReadableSsid) {
      return NetworkKind.WIFI
    }
    if (hasAnyWifi) {
      return NetworkKind.WIFI_SETTLING
    }
    if (hasCellular) {
      return NetworkKind.MOBILE
    }
    return NetworkKind.OTHER
  }
}
