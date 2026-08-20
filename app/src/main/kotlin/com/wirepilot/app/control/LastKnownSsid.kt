package com.wirepilot.app.control

import com.wirepilot.app.data.LastKnownSsidStore
import com.wirepilot.app.data.StoredLastKnownSsid

/** Last readable SSID is kept until a different readable SSID is stored. No TTL. */
class LastKnownSsid(
  private val store: LastKnownSsidStore,
  private val clock: () -> Long = { 0L },
) {
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
    return store.read()?.ssid
  }
}
