package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkKindClassifierTest {
  @Test
  fun readableSsidIsWifiEvenWithCellular() {
    assertEquals(
      NetworkKind.WIFI,
      NetworkKindClassifier.classify(
        hasReadableSsid = true,
        hasAnyWifi = true,
        hasCellular = true,
      ),
    )
  }

  @Test
  fun wifiWithoutSsidIsSettling() {
    assertEquals(
      NetworkKind.WIFI_SETTLING,
      NetworkKindClassifier.classify(
        hasReadableSsid = false,
        hasAnyWifi = true,
        hasCellular = true,
      ),
    )
  }

  @Test
  fun cellularWithoutWifiIsMobile() {
    assertEquals(
      NetworkKind.MOBILE,
      NetworkKindClassifier.classify(
        hasReadableSsid = false,
        hasAnyWifi = false,
        hasCellular = true,
      ),
    )
  }

  @Test
  fun noneIsOther() {
    assertEquals(
      NetworkKind.OTHER,
      NetworkKindClassifier.classify(
        hasReadableSsid = false,
        hasAnyWifi = false,
        hasCellular = false,
      ),
    )
  }
}
