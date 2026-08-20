package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastKnownSsidTest {
  @Test
  fun settlingUsesRememberedSsid() {
    var now = 1_000L
    val cache = LastKnownSsid(clock = { now })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val settling = NetworkSnapshot(NetworkKind.WIFI_SETTLING, hasCellular = true)
    val restored = cache.takeIfSettling(settling)
    assertEquals(NetworkKind.WIFI, restored.kind)
    assertEquals(setOf("Home"), restored.wifiSsids)
    assertEquals("lastKnown", restored.ssidSource)
    assertEquals(true, restored.hasCellular)
  }

  @Test
  fun expiredCacheLeavesSettling() {
    var now = 1_000L
    val cache = LastKnownSsid(clock = { now })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    now += LastKnownSsid.TTL_MS + 1
    val settling = NetworkSnapshot(NetworkKind.WIFI_SETTLING)
    assertEquals(settling, cache.takeIfSettling(settling))
    assertNull(cache.current())
  }

  @Test
  fun readableWifiIsUnchanged() {
    val cache = LastKnownSsid(clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val live = NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe"), ssidSource = "connectionInfo")
    assertEquals(live, cache.takeIfSettling(live))
  }

  @Test
  fun mobileIsUnchanged() {
    val cache = LastKnownSsid(clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    val mobile = NetworkSnapshot(NetworkKind.MOBILE, hasCellular = true)
    assertEquals(mobile, cache.takeIfSettling(mobile))
  }

  @Test
  fun newerReadableSsidReplacesCache() {
    var now = 1_000L
    val cache = LastKnownSsid(clock = { now })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    now += 10
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe")))
    val restored = cache.takeIfSettling(NetworkSnapshot(NetworkKind.WIFI_SETTLING))
    assertEquals(setOf("Cafe"), restored.wifiSsids)
  }

  @Test
  fun emptyWifiSsidsDoNotClearCache() {
    val cache = LastKnownSsid(clock = { 1L })
    cache.remember(NetworkSnapshot(NetworkKind.WIFI, setOf("Home")))
    cache.remember(NetworkSnapshot(NetworkKind.WIFI_SETTLING))
    assertEquals("Home", cache.current())
  }
}
