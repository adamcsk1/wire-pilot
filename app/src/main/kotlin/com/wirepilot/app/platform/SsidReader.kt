package com.wirepilot.app.platform

import android.net.ConnectivityManager
import android.net.Network
import com.wirepilot.app.control.InventoryMapper
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.LastKnownRememberPolicy
import com.wirepilot.app.control.NetworkKind
import com.wirepilot.app.control.NetworkSnapshot
import com.wirepilot.app.control.SsidProbeFormatter
import com.wirepilot.app.control.SsidReadiness

class SsidReader(
  private val inventory: NetworkInventory,
  private val connectivityManager: ConnectivityManager,
  private val readiness: () -> SsidReadiness,
  private val lastKnown: LastKnownSsid,
) {
  private var rememberedReadableRevision = 0L

  @Synchronized
  fun snapshot(): NetworkSnapshot {
    lastKnown.expireIfStale()
    refreshKnownNetworks()
    val observationState = inventory.state()
    val snapshot = InventoryMapper.toSnapshot(
      observationState.observations.map { observation -> observation.link },
      ssidSource = "transport",
    )
    val withProbe = snapshot.copy(
      probe = SsidProbeFormatter.format(
        readiness = readiness(),
        links = observationState.observations.map { observation -> observation.probe },
      ),
    )
    if (withProbe.kind == NetworkKind.WIFI) {
      if (LastKnownRememberPolicy.shouldRemember(
          snapshot = withProbe,
          readableRevision = observationState.readableRevision,
          rememberedRevision = rememberedReadableRevision,
        )) {
        lastKnown.remember(withProbe)
        rememberedReadableRevision = observationState.readableRevision
      }
      return withProbe
    }
    return lastKnown.takeIfSettling(withProbe)
  }

  private fun refreshKnownNetworks() {
    val scan = inventory.beginScan()
    val present = linkedSetOf<Network>()
    currentNetworks().forEach { network ->
      present += network
      refresh(network)
    }
    offerPresent(connectivityManager.activeNetwork, present)
    inventory.removeMissing(present, scan)
  }

  private fun refresh(network: Network) {
    val revision = inventory.beginRefresh(network)
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    inventory.observeRefresh(network, revision, capabilities)
  }

  @Suppress("DEPRECATION")
  private fun currentNetworks(): Array<Network> {
    return connectivityManager.allNetworks
  }

  private fun offerPresent(network: Network?, present: MutableSet<Network>) {
    if (network == null || !present.add(network)) {
      return
    }
    refresh(network)
  }
}
