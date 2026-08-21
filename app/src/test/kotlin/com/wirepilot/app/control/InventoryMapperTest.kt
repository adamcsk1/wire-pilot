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
