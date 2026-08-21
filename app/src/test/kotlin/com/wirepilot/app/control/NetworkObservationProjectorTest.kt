package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkObservationProjectorTest {
  @Test
  fun vpnTransportIsRejected() {
    assertEquals(
      null,
      NetworkObservationProjector.project(transport(wifi = false, cellular = false, vpn = true)),
    )
  }

  @Test
  fun readableWifiSsidIsProjected() {
    val projected = NetworkObservationProjector.project(
      transport(wifi = true, cellular = false, vpn = false, ssidRaw = "Home"),
    )

    assertEquals("Home", projected?.link?.rawSsid)
    assertEquals("Home", projected?.probe?.ssidRaw)
  }

  @Test
  fun wifiSsidFallbackWinsWhenLegacySsidIsRedacted() {
    val projected = NetworkObservationProjector.project(
      transport(
        wifi = true,
        cellular = false,
        vpn = false,
        ssidRaw = "<unknown ssid>",
        wifiSsidRaw = "Cafe",
      ),
    )

    assertEquals("Cafe", projected?.link?.rawSsid)
  }

  @Test
  fun cellularProjectionDoesNotExposeIrrelevantSsid() {
    val projected = NetworkObservationProjector.project(
      transport(wifi = false, cellular = true, vpn = false, ssidRaw = "Home"),
    )

    assertEquals(null, projected?.link?.rawSsid)
    assertEquals(true, projected?.link?.cellular)
  }

  @Test
  fun unreadableValuesRetainPrimaryRawValueForDiagnostics() {
    val projected = NetworkObservationProjector.project(
      transport(
        wifi = true,
        cellular = false,
        vpn = false,
        ssidRaw = "<unknown ssid>",
        wifiSsidRaw = null,
      ),
    )

    assertEquals("<unknown ssid>", projected?.link?.rawSsid)
  }

  @Test
  fun secondaryUnreadableValueIsRetainedWhenPrimaryIsNull() {
    val projected = NetworkObservationProjector.project(
      transport(
        wifi = true,
        cellular = false,
        vpn = false,
        ssidRaw = null,
        wifiSsidRaw = "<unknown ssid>",
      ),
    )

    assertEquals("<unknown ssid>", projected?.link?.rawSsid)
  }

  private fun transport(
    wifi: Boolean,
    cellular: Boolean,
    vpn: Boolean,
    ssidRaw: String? = null,
    wifiSsidRaw: String? = null,
  ): NetworkTransportObservation {
    return NetworkTransportObservation(
      wifi = wifi,
      cellular = cellular,
      vpn = vpn,
      transportClass = if (wifi) "WifiInfo" else "null",
      ssidRaw = ssidRaw,
      wifiSsidRaw = wifiSsidRaw,
    )
  }
}
