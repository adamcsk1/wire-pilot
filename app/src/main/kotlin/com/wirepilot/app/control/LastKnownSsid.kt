package com.wirepilot.app.control

import com.wirepilot.app.data.LastKnownSsidStore
import com.wirepilot.app.data.StoredLastKnownSsid

/** Last readable SSID applies only while still inside [TTL_MILLIS]. */
class LastKnownSsid(
  private val store: LastKnownSsidStore,
  private val clock: () -> Long = { 0L },
) {
  fun expireIfStale() {
    current()
  }

  fun remember(snapshot: NetworkSnapshot) {
    val name = snapshot.wifiSsids.minOrNull() ?: return
    store.write(StoredLastKnownSsid(ssid = name, atMillis = clock()))
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
    val stored = store.read() ?: return null
    if (clock() - stored.atMillis > TTL_MILLIS) {
      store.clear()
      return null
    }
    return stored.ssid
  }

  companion object {
    const val TTL_MILLIS = 60_000L
  }
}
