package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class InventoryMapperTest {
  @Test
  fun readableWifiAndCell() {
    val snapshot = InventoryMapper.toSnapshot(
      listOf(
        InventoryLink(wifi = true, cellular = false, rawSsid = "Home"),
        InventoryLink(wifi = false, cellular = true, rawSsid = null),
      ),
    )
    assertEquals(NetworkKind.WIFI, snapshot.kind)
    assertEquals(setOf("Home"), snapshot.wifiSsids)
    assertEquals(true, snapshot.hasCellular)
  }

  @Test
  fun unknownWifiIsSettling() {
    val snapshot = InventoryMapper.toSnapshot(
      listOf(InventoryLink(wifi = true, cellular = true, rawSsid = "<unknown ssid>")),
    )
    assertEquals(NetworkKind.WIFI_SETTLING, snapshot.kind)
  }

  @Test
  fun cellOnlyIsMobile() {
    val snapshot = InventoryMapper.toSnapshot(
      listOf(InventoryLink(wifi = false, cellular = true, rawSsid = null)),
    )
    assertEquals(NetworkKind.MOBILE, snapshot.kind)
  }
}

class ConnectionInfoFallbackTest {
  @Test
  fun rejectsWhenNoWifi() {
    assertEquals(
      false,
      ConnectionInfoFallback.allow(listOf(InventoryLink(wifi = false, cellular = true, rawSsid = null))),
    )
  }

  @Test
  fun allowsWhenWifiSsidIsRedacted() {
    assertEquals(
      true,
      ConnectionInfoFallback.allow(
        listOf(InventoryLink(wifi = true, cellular = false, rawSsid = "<unknown ssid>")),
      ),
    )
  }

  @Test
  fun allowsWhenWifiHasNoTransportSsid() {
    assertEquals(
      true,
      ConnectionInfoFallback.allow(
        listOf(InventoryLink(wifi = true, cellular = false, rawSsid = null)),
      ),
    )
  }

  @Test
  fun rejectsWhenAnyWifiLinkIsReadable() {
    assertEquals(
      false,
      ConnectionInfoFallback.allow(
        listOf(
          InventoryLink(wifi = true, cellular = false, rawSsid = "<unknown ssid>"),
          InventoryLink(wifi = true, cellular = false, rawSsid = "Home"),
        ),
      ),
    )
  }
}
