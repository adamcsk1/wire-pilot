package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class SsidProbeFormatterTest {
  @Test
  fun formatsReadinessCountsAndConnection() {
    val detail = SsidProbeFormatter.format(
      readiness = SsidReadiness(
        nearbyWifiGranted = true,
        fineLocationGranted = false,
        locationEnabled = true,
      ),
      links = listOf(
        SsidProbeLink(
          wifi = true,
          cellular = false,
          vpn = false,
          transportClass = "WifiInfo",
          ssidRaw = "<unknown ssid>",
          wifiSsidRaw = null,
        ),
        SsidProbeLink(
          wifi = false,
          cellular = true,
          vpn = false,
          transportClass = "null",
          ssidRaw = null,
          wifiSsidRaw = null,
        ),
        SsidProbeLink(
          wifi = false,
          cellular = false,
          vpn = true,
          transportClass = "VpnTransportInfo",
          ssidRaw = null,
          wifiSsidRaw = null,
        ),
        SsidProbeLink(
          wifi = false,
          cellular = false,
          vpn = false,
          transportClass = "null",
          ssidRaw = null,
          wifiSsidRaw = null,
        ),
      ),
      connectionSsid = "<unknown ssid>",
      connectionWifiSsid = null,
    )
    assertEquals(
      "nearby=T fine=F locOn=T links=w1,c1,v1,o1 " +
        "[wifi=T cell=F vpn=F transport=WifiInfo ssidRaw=<unknown ssid> wifiSsid=null] " +
        "[wifi=F cell=T vpn=F transport=null ssidRaw=null wifiSsid=null] " +
        "[wifi=F cell=F vpn=T transport=VpnTransportInfo ssidRaw=null wifiSsid=null] " +
        "[wifi=F cell=F vpn=F transport=null ssidRaw=null wifiSsid=null] " +
        "conn.ssid=<unknown ssid> conn.wifiSsid=null",
      detail,
    )
  }

  @Test
  fun emptyLinksStillReportCounts() {
    val detail = SsidProbeFormatter.format(
      readiness = SsidReadiness(
        nearbyWifiGranted = false,
        fineLocationGranted = false,
        locationEnabled = false,
      ),
      links = emptyList(),
      connectionSsid = null,
      connectionWifiSsid = null,
    )
    assertEquals(
      "nearby=F fine=F locOn=F links=w0,c0,v0,o0 conn.ssid=null conn.wifiSsid=null",
      detail,
    )
  }
}
