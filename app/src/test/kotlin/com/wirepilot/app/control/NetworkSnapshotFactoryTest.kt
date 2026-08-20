package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkSnapshotFactoryTest {
  @Test
  fun noWifiNoCellIsOther() {
    assertEquals(
      NetworkSnapshot(kind = NetworkKind.OTHER, wifiSsids = emptySet(), hasCellular = false),
      NetworkSnapshotFactory.fromRawWifiSsids(emptyList(), hasAnyWifi = false, hasCellular = false),
    )
  }

  @Test
  fun wifiWithoutReadableSsidIsSettling() {
    assertEquals(
      NetworkSnapshot(kind = NetworkKind.WIFI_SETTLING, wifiSsids = emptySet(), hasCellular = true),
      NetworkSnapshotFactory.fromRawWifiSsids(
        listOf("<unknown ssid>"),
        hasAnyWifi = true,
        hasCellular = true,
      ),
    )
  }

  @Test
  fun readableSsidIsWifi() {
    assertEquals(
      NetworkSnapshot(kind = NetworkKind.WIFI, wifiSsids = setOf("Home", "Cafe"), hasCellular = true),
      NetworkSnapshotFactory.fromRawWifiSsids(
        listOf("\"Home\"", "Cafe", "Home"),
        hasAnyWifi = true,
        hasCellular = true,
      ),
    )
  }

  @Test
  fun cellularOnlyIsMobile() {
    assertEquals(
      NetworkSnapshot(kind = NetworkKind.MOBILE, wifiSsids = emptySet(), hasCellular = true),
      NetworkSnapshotFactory.fromRawWifiSsids(emptyList(), hasAnyWifi = false, hasCellular = true),
    )
  }
}
