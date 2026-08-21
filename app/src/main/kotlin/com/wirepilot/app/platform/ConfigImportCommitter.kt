package com.wirepilot.app.platform

import com.wirepilot.app.data.ConfigImportBatch
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog

class ConfigImportCommitter(
  private val catalog: TunnelCatalog,
  private val splitTunnels: SplitTunnelStore,
) {
  fun commit(batch: ConfigImportBatch, shouldContinue: () -> Boolean = { true }): List<String> {
    val snapshots = batch.tunnels.associate { tunnel ->
      tunnel.name to Snapshot(catalog.readConf(tunnel.name), splitTunnels.read(tunnel.name))
    }
    return try {
      batch.tunnels.forEach { tunnel ->
        check(shouldContinue())
        catalog.writeConf(tunnel.name, tunnel.conf)
        splitTunnels.write(tunnel.name, tunnel.splitTunnel)
      }
      batch.tunnels.map { tunnel -> tunnel.name }
    } catch (_: Exception) {
      val restored = snapshots.all { (name, snapshot) ->
        runCatching {
          if (snapshot.conf == null) {
            catalog.delete(name)
            splitTunnels.delete(name)
          } else {
            catalog.writeConf(name, snapshot.conf)
            splitTunnels.write(name, snapshot.splitTunnel)
          }
        }.isSuccess
      }
      check(restored)
      emptyList()
    }
  }

  private data class Snapshot(
    val conf: String?,
    val splitTunnel: StoredSplitTunnel,
  )
}
