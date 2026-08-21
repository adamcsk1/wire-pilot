package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkObservationMergerTest {
  @Test
  fun authoritativeSsidSurvivesQueryMetadataRefresh() {
    val callback = observation(
      wifi = true,
      cellular = false,
      rawSsid = "Home",
      wifiSsidRaw = "Home",
      transportClass = "WifiInfo",
    )
    val query = observation(
      wifi = true,
      cellular = true,
      rawSsid = "<unknown ssid>",
      wifiSsidRaw = null,
      transportClass = "RedactedWifiInfo",
    )

    val merged = NetworkObservationMerger.refreshAuthoritative(callback, query)

    assertEquals(true, merged.link.cellular)
    assertEquals("RedactedWifiInfo", merged.probe.transportClass)
    assertEquals("Home", merged.link.rawSsid)
    assertEquals("Home", merged.probe.ssidRaw)
    assertEquals("Home", merged.probe.wifiSsidRaw)
  }

  @Test
  fun authoritativeRedactionCannotBeBypassedByReadableQuery() {
    val callback = observation(
      wifi = true,
      cellular = false,
      rawSsid = "<unknown ssid>",
      wifiSsidRaw = null,
      transportClass = "WifiInfo",
    )
    val query = observation(
      wifi = true,
      cellular = true,
      rawSsid = "Home",
      wifiSsidRaw = "Home",
      transportClass = "WifiInfo",
    )

    val merged = NetworkObservationMerger.refreshAuthoritative(callback, query)

    assertEquals("<unknown ssid>", merged.link.rawSsid)
    assertEquals("<unknown ssid>", merged.probe.ssidRaw)
    assertEquals(null, merged.probe.wifiSsidRaw)
    assertEquals(true, merged.link.cellular)
  }

  private fun observation(
    wifi: Boolean,
    cellular: Boolean,
    rawSsid: String?,
    wifiSsidRaw: String?,
    transportClass: String,
  ): NetworkObservation {
    return NetworkObservation(
      link = InventoryLink(wifi = wifi, cellular = cellular, rawSsid = rawSsid),
      probe = SsidProbeLink(
        wifi = wifi,
        cellular = cellular,
        vpn = false,
        transportClass = transportClass,
        ssidRaw = rawSsid,
        wifiSsidRaw = wifiSsidRaw,
      ),
    )
  }
}
