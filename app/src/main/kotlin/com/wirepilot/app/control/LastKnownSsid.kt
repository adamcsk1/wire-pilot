package com.wirepilot.app.control

class LastKnownSsid(
  private val clock: () -> Long,
  private val ttlMillis: Long = TTL_MS,
) {
  private var ssid: String? = null
  private var rememberedAtMillis: Long = 0

  fun remember(snapshot: NetworkSnapshot) {
    val name = snapshot.wifiSsids.minOrNull() ?: return
    ssid = name
    rememberedAtMillis = clock()
  }

  fun takeIfSettling(snapshot: NetworkSnapshot): NetworkSnapshot {
    if (snapshot.kind != NetworkKind.WIFI_SETTLING) {
      return snapshot
    }
    val name = current() ?: return snapshot
    return snapshot.copy(
      kind = NetworkKind.WIFI,
      wifiSsids = setOf(name),
      ssidSource = "lastKnown",
    )
  }

  fun current(): String? {
    val name = ssid ?: return null
    if (clock() - rememberedAtMillis > ttlMillis) {
      return null
    }
    return name
  }

  companion object {
    const val TTL_MS = 5 * 60_000L
  }
}
