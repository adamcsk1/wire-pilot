package com.wirepilot.app.platform

import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import com.wirepilot.app.control.InventoryLink
import com.wirepilot.app.control.NetworkObservation
import com.wirepilot.app.control.NetworkObservationLedger
import com.wirepilot.app.control.NetworkObservationProjector
import com.wirepilot.app.control.NetworkObservationState
import com.wirepilot.app.control.NetworkTransportObservation
import com.wirepilot.app.control.SsidProbeLink

class NetworkInventory {
  private val observations = NetworkObservationLedger<Network>()

  fun observeCallback(network: Network, capabilities: NetworkCapabilities) {
    observations.observe(network, observation(capabilities))
  }

  fun beginRefresh(network: Network): Long = observations.beginRefresh(network)

  fun observeRefresh(network: Network, revision: Long, capabilities: NetworkCapabilities?) {
    observations.refresh(network, revision, capabilities?.let(::observation))
  }

  fun remove(network: Network) {
    observations.observe(network, null)
  }

  fun beginScan(): NetworkObservationLedger.ScanToken = observations.beginScan()

  fun removeMissing(networks: Set<Network>, scanToken: NetworkObservationLedger.ScanToken) {
    observations.removeMissing(networks, scanToken)
  }

  fun networks(): Set<Network> = observations.keys()

  fun links(): List<InventoryLink> {
    return observations.values().map { observation -> observation.link }
  }

  fun probeLinks(): List<SsidProbeLink> {
    return observations.values().map { observation -> observation.probe }
  }

  fun state(): NetworkObservationState = observations.state()

  private fun observation(capabilities: NetworkCapabilities): NetworkObservation? {
    val wifiInfo = capabilities.transportInfo as? WifiInfo
    return NetworkObservationProjector.project(
      NetworkTransportObservation(
        wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        transportClass = capabilities.transportInfo?.javaClass?.simpleName ?: "null",
        ssidRaw = wifiInfo?.ssid,
        wifiSsidRaw = wifiSsidRaw(wifiInfo),
      ),
    )
  }
}

internal fun wifiSsidRaw(wifiInfo: WifiInfo?): String? {
  if (wifiInfo == null) {
    return null
  }
  val wifiSsid = runCatching {
    wifiInfo.javaClass.getMethod("getWifiSsid").invoke(wifiInfo)
  }.getOrNull() ?: return null
  val bytes = runCatching {
    wifiSsid.javaClass.getMethod("getBytes").invoke(wifiSsid) as ByteArray
  }.getOrNull()
  if (bytes != null && bytes.isNotEmpty()) {
    return bytes.toString(Charsets.UTF_8)
  }
  return wifiSsid.toString().takeIf { text -> text.isNotBlank() }
}
