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
    } catch (error: Exception) {
      val rollbackFailure = IllegalStateException("Import rollback incomplete", error)
      snapshots.forEach { (name, snapshot) ->
        runCatching {
          if (snapshot.conf == null) {
            catalog.delete(name)
          } else {
            catalog.writeConf(name, snapshot.conf)
          }
        }.onFailure { rollbackFailure.addSuppressed(it) }
        runCatching {
          if (snapshot.conf == null) {
            splitTunnels.delete(name)
          } else {
            splitTunnels.write(name, snapshot.splitTunnel)
          }
        }.onFailure { rollbackFailure.addSuppressed(it) }
      }
      if (rollbackFailure.suppressed.isNotEmpty()) throw rollbackFailure
      emptyList()
    }
  }

  private data class Snapshot(
    val conf: String?,
    val splitTunnel: StoredSplitTunnel,
  )
}
