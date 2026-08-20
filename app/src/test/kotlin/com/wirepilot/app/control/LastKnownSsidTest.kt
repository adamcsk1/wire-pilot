package com.wirepilot.app.control

import com.wirepilot.app.data.LastKnownSsidStore
import com.wirepilot.app.data.StoredLastKnownSsid
import kotlin.test.Test
import kotlin.test.assertEquals

class LastKnownSsidTest {
  private class MemoryStore : LastKnownSsidStore {
    var value: StoredLastKnownSsid? = null

    override fun read(): StoredLastKnownSsid? = value

    override fun write(value: StoredLastKnownSsid) {
      this.value = value
    }
  }

  @Test
  fun settlingUsesRememberedSsid() {
    val store = MemoryStore()
    val cache = LastKnownSsid(store = store, clock = { 1_000L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val settling = NetworkSnapshot(NetworkKind.WIFI_SETTLING, hasCellular = true)
    val restored = cache.takeIfSettling(settling)
    assertEquals(NetworkKind.WIFI, restored.kind)
    assertEquals(setOf("Home"), restored.wifiSsids)
    assertEquals("lastKnown", restored.ssidSource)
    assertEquals(true, restored.hasCellular)
  }

  @Test
  fun doesNotExpireWithTime() {
    var now = 1_000L
    val store = MemoryStore()
    val cache = LastKnownSsid(store = store, clock = { now })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    now += 30L * 24 * 60 * 60 * 1000
    assertEquals("Home", cache.current())
  }

  @Test
  fun survivesNewInstanceOnSameStore() {
    val store = MemoryStore()
    LastKnownSsid(store = store, clock = { 1L }).remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val restored = LastKnownSsid(store = store).takeIfSettling(NetworkSnapshot(NetworkKind.WIFI_SETTLING))
    assertEquals(setOf("Home"), restored.wifiSsids)
    assertEquals("lastKnown", restored.ssidSource)
  }

  @Test
  fun readableWifiIsUnchanged() {
    val cache = LastKnownSsid(store = MemoryStore(), clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val live = NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe"), ssidSource = "connectionInfo")
    assertEquals(live, cache.takeIfSettling(live))
  }

  @Test
  fun mobileIsUnchanged() {
    val cache = LastKnownSsid(store = MemoryStore(), clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val mobile = NetworkSnapshot(NetworkKind.MOBILE, hasCellular = true)
    assertEquals(mobile, cache.takeIfSettling(mobile))
  }

  @Test
  fun newerReadableSsidReplacesCache() {
    val store = MemoryStore()
    val cache = LastKnownSsid(store = store, clock = { 10L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")))
    val restored = cache.takeIfSettling(NetworkSnapshot(NetworkKind.WIFI_SETTLING))
    assertEquals(setOf("Cafe"), restored.wifiSsids)
    assertEquals("Cafe", store.value?.ssid)
  }

  @Test
  fun emptyWifiSsidsDoNotClearCache() {
    val cache = LastKnownSsid(store = MemoryStore(), clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    cache.remember(NetworkSnapshot(NetworkKind.WIFI_SETTLING))
    assertEquals("Home", cache.current())
  }
}
