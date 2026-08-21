package com.wirepilot.app.control

data class SsidProbeLink(
  val wifi: Boolean,
  val cellular: Boolean,
  val vpn: Boolean,
  val transportClass: String,
  val ssidRaw: String?,
  val wifiSsidRaw: String?,
)

object SsidProbeFormatter {
  fun format(
    readiness: SsidReadiness,
    links: List<SsidProbeLink>,
  ): String {
    val wifiCount = links.count { it.wifi }
    val cellularCount = links.count { it.cellular }
    val vpnCount = links.count { it.vpn }
    val otherCount = links.count { !it.wifi && !it.cellular && !it.vpn }
    val linkText = links.joinToString(" ") { link ->
      "[wifi=${flag(link.wifi)} cell=${flag(link.cellular)} vpn=${flag(link.vpn)} " +
        "transport=${link.transportClass} ssidRaw=${SsidRedactor.redactNullable(link.ssidRaw)} wifiSsid=${SsidRedactor.redactNullable(link.wifiSsidRaw)}]"
    }
    val linksPart = if (linkText.isBlank()) "" else " $linkText"
    return "nearby=${flag(readiness.nearbyWifiGranted)} " +
      "fine=${flag(readiness.fineLocationGranted)} " +
      "locOn=${flag(readiness.locationEnabled)} " +
      "links=w$wifiCount,c$cellularCount,v$vpnCount,o$otherCount" +
      linksPart
  }

  private fun flag(value: Boolean): String {
    return if (value) "T" else "F"
  }
}
