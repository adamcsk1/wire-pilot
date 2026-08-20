package com.wirepilot.app.control

object NetworkSnapshotFactory {
  fun fromRawWifiSsids(
    rawSsids: List<String?>,
    hasAnyWifi: Boolean,
    hasCellular: Boolean,
    ssidSource: String = "none",
  ): NetworkSnapshot {
    val wifiSsids = rawSsids.mapNotNull(SsidNormalizer::normalize).toSet()
    val kind = NetworkKindClassifier.classify(
      hasReadableSsid = wifiSsids.isNotEmpty(),
      hasAnyWifi = hasAnyWifi,
      hasCellular = hasCellular,
    )
    return NetworkSnapshot(
      kind = kind,
      wifiSsids = if (kind == NetworkKind.WIFI) wifiSsids else emptySet(),
      hasCellular = hasCellular,
      ssidSource = ssidSource,
    )
  }
}
