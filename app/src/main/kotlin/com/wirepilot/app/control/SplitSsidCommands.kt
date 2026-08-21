package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog

class SplitSsidCommands(
  private val store: ControlStore,
  private val applyRunner: ApplyRunner,
  private val splitTunnels: SplitTunnelStore,
  private val excludedSsids: ExcludedSsidStore,
  private val catalog: TunnelCatalog,
  private val resolver: ControlResolver,
) {
  fun splitSettings(tunnelName: String): StoredSplitTunnel {
    return splitTunnels.read(tunnelName)
  }

  fun setSplitTunnel(mode: SplitTunnelMode, packages: Set<String>, tunnelName: String) {
    if (tunnelName.isBlank()) {
      return
    }
    val selection = SplitTunnelPolicy.selection(mode, packages)
    val storedMode = SplitTunnelPolicy.modeFrom(selection.excludedPackages, selection.includedPackages)
    val storedPackages = selection.excludedPackages + selection.includedPackages
    splitTunnels.write(tunnelName, StoredSplitTunnel(storedMode, storedPackages))
    val resolved = resolver.persistResolved()
    if (resolved.tunnelName == tunnelName || resolved.mobileTunnelName == tunnelName) {
      applyRunner.applyNow("split-tunnel")
    }
  }

  fun excludedSsids(tunnelName: String): Set<String> {
    return excludedSsids.read(tunnelName)
  }

  fun addExcludedSsid(raw: String, tunnelName: String): Boolean {
    if (tunnelName.isBlank()) {
      return false
    }
    val current = excludedSsids.read(tunnelName)
    val nextSsids = SsidList.add(current, raw)
    if (nextSsids == current) {
      return false
    }
    excludedSsids.write(tunnelName, nextSsids)
    if (resolver.persistResolved().tunnelName == tunnelName) {
      applyRunner.applyNow("ssid-add")
    }
    return true
  }

  fun removeExcludedSsid(raw: String, tunnelName: String) {
    if (tunnelName.isBlank()) {
      return
    }
    excludedSsids.write(tunnelName, SsidList.remove(excludedSsids.read(tunnelName), raw))
    if (resolver.persistResolved().tunnelName == tunnelName) {
      applyRunner.applyNow("ssid-remove")
    }
  }

  fun setConnectOnMobile(enabled: Boolean, tunnelName: String) {
    if (tunnelName.isBlank()) {
      return
    }
    val names = catalog.names()
    if (names.isNotEmpty() && tunnelName !in names) {
      return
    }
    val current = resolver.persistResolved()
    val nextMobile = if (enabled) {
      tunnelName
    } else if (current.mobileTunnelName == tunnelName) {
      ""
    } else {
      current.mobileTunnelName
    }
    store.write(current.copy(mobileTunnelName = nextMobile))
    applyRunner.applyNow("mobile-flag")
  }
}
