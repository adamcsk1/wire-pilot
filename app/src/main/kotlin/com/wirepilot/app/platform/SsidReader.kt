package com.wirepilot.app.platform

import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import com.wirepilot.app.control.ConnectionInfoFallback
import com.wirepilot.app.control.InventoryLink
import com.wirepilot.app.control.InventoryMapper
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.NetworkKind
import com.wirepilot.app.control.NetworkSnapshot
import com.wirepilot.app.data.SsidNormalizer
import com.wirepilot.app.control.SsidProbeFormatter
import com.wirepilot.app.control.SsidReadiness

class SsidReader(
  private val inventory: NetworkInventory,
  private val connectivityManager: ConnectivityManager,
  private val wifiManager: WifiManager,
  private val readiness: () -> SsidReadiness,
  private val lastKnown: LastKnownSsid,
) {
  fun snapshot(): NetworkSnapshot {
    lastKnown.expireIfStale()
    refreshKnownNetworks()
    offer(connectivityManager.activeNetwork)
    val links = inventory.links()
    val fromTransport = InventoryMapper.toSnapshot(links, ssidSource = "transport")
    val connection = connectionInfo()
    val snapshot = if (fromTransport.kind == NetworkKind.WIFI) {
      fromTransport
    } else if (ConnectionInfoFallback.allow(links)) {
      fallbackFromConnection(links, fromTransport, connection)
    } else {
      fromTransport
    }
    val withProbe = snapshot.copy(
      probe = SsidProbeFormatter.format(
        readiness = readiness(),
        links = inventory.probeLinks(),
        connectionSsid = connection.ssid,
        connectionWifiSsid = connection.wifiSsid,
      ),
    )
    if (withProbe.kind == NetworkKind.WIFI) {
      lastKnown.remember(withProbe)
      return withProbe
    }
    return lastKnown.takeIfSettling(withProbe)
  }

  private fun fallbackFromConnection(
    links: List<InventoryLink>,
    fromTransport: NetworkSnapshot,
    connection: ConnectionSsid,
  ): NetworkSnapshot {
    val raw = when {
      SsidNormalizer.normalize(connection.ssid) != null -> connection.ssid
      SsidNormalizer.normalize(connection.wifiSsid) != null -> connection.wifiSsid
      else -> null
    }
    if (raw == null) {
      return fromTransport
    }
    return InventoryMapper.toSnapshot(
      links + InventoryLink(wifi = true, cellular = fromTransport.hasCellular, rawSsid = raw),
      ssidSource = "connectionInfo",
    )
  }

  private fun refreshKnownNetworks() {
    inventory.networks().forEach { network ->
      val capabilities = connectivityManager.getNetworkCapabilities(network)
      if (capabilities == null) {
        inventory.remove(network)
      } else {
        inventory.put(network, capabilities)
      }
    }
  }

  private fun offer(network: Network?) {
    if (network == null) {
      return
    }
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
    inventory.put(network, capabilities)
  }

  @Suppress("DEPRECATION")
  private fun connectionInfo(): ConnectionSsid {
    val info = runCatching { wifiManager.connectionInfo }.getOrNull()
    return ConnectionSsid(
      ssid = info?.ssid,
      wifiSsid = wifiSsidRaw(info),
    )
  }

  private data class ConnectionSsid(
    val ssid: String?,
    val wifiSsid: String?,
  )
}
