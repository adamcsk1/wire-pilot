package com.wirepilot.app.control

data class NetworkSnapshot(
  val kind: NetworkKind,
  val wifiSsids: Set<String> = emptySet(),
  val hasCellular: Boolean = false,
  val ssidSource: String = "none",
  val probe: String = "",
) {
  val connectedToWifi: Boolean
    get() = kind == NetworkKind.WIFI || kind == NetworkKind.WIFI_SETTLING
}
