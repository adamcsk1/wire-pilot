package com.wirepilot.app.platform

import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import com.wirepilot.app.control.InventoryLink
import com.wirepilot.app.control.SsidNormalizer
import com.wirepilot.app.control.SsidProbeLink
import java.util.concurrent.ConcurrentHashMap

class NetworkInventory {
  private val capabilitiesByNetwork = ConcurrentHashMap<Network, NetworkCapabilities>()

  fun put(network: Network, capabilities: NetworkCapabilities) {
    capabilitiesByNetwork[network] = capabilities
  }

  fun remove(network: Network) {
    capabilitiesByNetwork.remove(network)
  }

  fun networks(): Set<Network> {
    return capabilitiesByNetwork.keys.toSet()
  }

  fun links(): List<InventoryLink> {
    return capabilitiesByNetwork.values.map { capabilities ->
      val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
      InventoryLink(
        wifi = wifi,
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        rawSsid = if (wifi) readableSsidFrom(capabilities) else null,
      )
    }
  }

  fun probeLinks(): List<SsidProbeLink> {
    return capabilitiesByNetwork.values.map { capabilities ->
      val wifiInfo = capabilities.transportInfo as? WifiInfo
      SsidProbeLink(
        wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        transportClass = capabilities.transportInfo?.javaClass?.simpleName ?: "null",
        ssidRaw = wifiInfo?.ssid,
        wifiSsidRaw = wifiSsidRaw(wifiInfo),
      )
    }
  }

  private fun readableSsidFrom(capabilities: NetworkCapabilities): String? {
    val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return null
    val ssid = wifiInfo.ssid
    if (SsidNormalizer.normalize(ssid) != null) {
      return ssid
    }
    val fromWifiSsid = wifiSsidRaw(wifiInfo)
    if (SsidNormalizer.normalize(fromWifiSsid) != null) {
      return fromWifiSsid
    }
    return ssid ?: fromWifiSsid
  }
}

internal fun wifiSsidRaw(wifiInfo: WifiInfo?): String? {
  if (wifiInfo == null) {
    return null
  }
  return runCatching {
    wifiInfo.javaClass.getMethod("getWifiSsid").invoke(wifiInfo)?.toString()
  }.getOrNull()
}
