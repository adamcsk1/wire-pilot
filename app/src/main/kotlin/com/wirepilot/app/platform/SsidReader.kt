package com.wirepilot.app.platform

import android.net.ConnectivityManager
import android.net.Network
import com.wirepilot.app.control.InventoryMapper
import com.wirepilot.app.control.LastKnownSsid
import com.wirepilot.app.control.LastKnownRememberPolicy
import com.wirepilot.app.control.NetworkKind
import com.wirepilot.app.control.NetworkSnapshot

class SsidReader(
  private val inventory: NetworkInventory,
  private val connectivityManager: ConnectivityManager,
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
    if (snapshot.kind == NetworkKind.WIFI) {
      if (LastKnownRememberPolicy.shouldRemember(
          snapshot = snapshot,
          readableRevision = observationState.readableRevision,
          rememberedRevision = rememberedReadableRevision,
        )) {
        lastKnown.remember(snapshot)
        rememberedReadableRevision = observationState.readableRevision
      }
      return snapshot
    }
    return lastKnown.takeIfSettling(snapshot)
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
