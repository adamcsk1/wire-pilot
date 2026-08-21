package com.wirepilot.app.support

import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog

class InMemoryTunnelCatalog(
  initial: Map<String, String> = emptyMap(),
) : TunnelCatalog {
  private val files = initial.toMutableMap()

  override fun names(): List<String> = files.keys.sorted()

  override fun readConf(name: String): String? = files[name]

  override fun writeConf(name: String, conf: String) {
    files[name] = conf
  }

  override fun delete(name: String) {
    files.remove(name)
  }
}

class InMemorySplitTunnelStore : SplitTunnelStore {
  private val values = mutableMapOf<String, StoredSplitTunnel>()

  override fun read(tunnelName: String): StoredSplitTunnel {
    return values[tunnelName] ?: StoredSplitTunnel()
  }

  override fun write(tunnelName: String, settings: StoredSplitTunnel) {
    values[tunnelName] = settings
  }

  override fun delete(tunnelName: String) {
    values.remove(tunnelName)
  }
}

class InMemoryExcludedSsidStore : ExcludedSsidStore {
  private val values = mutableMapOf<String, Set<String>>()

  override fun read(tunnelName: String): Set<String> {
    return values[tunnelName] ?: emptySet()
  }

  override fun write(tunnelName: String, ssids: Set<String>) {
    values[tunnelName] = ssids
  }

  override fun delete(tunnelName: String) {
    values.remove(tunnelName)
  }

  override fun exists(tunnelName: String): Boolean {
    return tunnelName in values
  }
}
