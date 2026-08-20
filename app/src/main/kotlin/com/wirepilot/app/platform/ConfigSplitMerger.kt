package com.wirepilot.app.platform

import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wirepilot.app.control.SplitTunnelPolicy
import com.wirepilot.app.data.StoredSplitTunnel

object ConfigSplitMerger {
  fun merge(config: Config, settings: StoredSplitTunnel): Config {
    val selection = SplitTunnelPolicy.selection(settings.mode, settings.packages)
    val current = config.`interface`
    val builder = Interface.Builder()
      .addAddresses(current.addresses)
      .addDnsServers(current.dnsServers)
      .addDnsSearchDomains(current.dnsSearchDomains)
      .setKeyPair(current.keyPair)
    current.listenPort.ifPresent { builder.setListenPort(it) }
    current.mtu.ifPresent { builder.setMtu(it) }
    if (selection.excludedPackages.isNotEmpty()) {
      builder.excludeApplications(selection.excludedPackages)
    }
    if (selection.includedPackages.isNotEmpty()) {
      builder.includeApplications(selection.includedPackages)
    }
    return Config.Builder()
      .setInterface(builder.build())
      .apply { config.peers.forEach { addPeer(it) } }
      .build()
  }

  fun toConf(config: Config): String {
    return config.toWgQuickString()
  }
}
