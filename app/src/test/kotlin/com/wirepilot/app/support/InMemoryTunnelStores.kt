package com.wirepilot.app.support

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
