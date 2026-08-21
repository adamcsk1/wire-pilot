package com.wirepilot.app.platform

import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import com.wirepilot.app.control.ConnectionInfoFallback
import com.wirepilot.app.control.InventoryLink
import com.wirepilot.app.control.InventoryMapper
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.LastKnownRememberPolicy
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
  private var rememberedReadableRevision = 0L

  @Synchronized
  fun snapshot(): NetworkSnapshot {
    lastKnown.expireIfStale()
    refreshKnownNetworks()
    offer(connectivityManager.activeNetwork)
    val observationState = inventory.state()
    val links = observationState.observations.map { observation -> observation.link }
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
        links = observationState.observations.map { observation -> observation.probe },
        connectionSsid = connection.ssid,
        connectionWifiSsid = connection.wifiSsid,
      ),
    )
    if (withProbe.kind == NetworkKind.WIFI) {
      if (LastKnownRememberPolicy.shouldRemember(
          snapshot = withProbe,
          readableRevision = observationState.readableRevision,
          rememberedRevision = rememberedReadableRevision,
        )) {
        lastKnown.remember(withProbe)
        if (withProbe.ssidSource != "connectionInfo") {
          rememberedReadableRevision = observationState.readableRevision
        }
      }
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

  @Suppress("DEPRECATION")
  private fun refreshKnownNetworks() {
    val scanToken = inventory.beginScan()
    val currentNetworks = runCatching { connectivityManager.allNetworks.toSet() }.getOrNull()
    if (currentNetworks == null) {
      inventory.networks().forEach { network -> refresh(network) }
      return
    }
    currentNetworks.forEach { network ->
      refresh(network)
    }
    inventory.removeMissing(currentNetworks, scanToken)
  }

  private fun refresh(network: Network) {
    val revision = inventory.beginRefresh(network)
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    inventory.observeRefresh(network, revision, capabilities)
  }

  private fun offer(network: Network?) {
    if (network == null) {
      return
    }
    refresh(network)
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
