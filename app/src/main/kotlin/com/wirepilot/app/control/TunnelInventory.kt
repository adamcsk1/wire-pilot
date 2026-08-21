package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.TunnelCatalog

class TunnelInventory(
  private val store: ControlStore,
  private val applyRunner: ApplyRunner,
  private val pauseAlarms: PauseAlarmPort,
  private val network: () -> NetworkSnapshot,
  private val catalog: TunnelCatalog,
  private val splitTunnels: SplitTunnelStore,
  private val excludedSsids: ExcludedSsidStore,
  private val tunnelState: TunnelStatePort,
  private val ssidMigration: () -> Unit,
  private val resolver: ControlResolver,
) {
  fun setTunnelName(name: String) {
    val current = resolver.persistResolved()
    val trimmed = name.trim()
    if (!ConfigZipNames.isValidTunnelName(trimmed) && trimmed.isNotEmpty()) {
      return
    }
    store.write(current.copy(tunnelName = trimmed))
  }

  fun selectImportedTunnel(name: String) {
    if (name !in catalog.names()) {
      return
    }
    val previous = resolver.persistResolved().tunnelName
    setTunnelName(name)
    if (resolver.persistResolved().enabled) {
      applyRunner.applyNow("tunnel-select")
    } else if (previous.isNotBlank() && previous != name) {
      applyRunner.force(TunnelCommand.DOWN, "tunnel-switch", previous)
    }
  }

  fun saveTunnel(name: String, conf: String, previousName: String? = null): TunnelSaveResult {
    val trimmed = name.trim()
    if (!ConfigZipNames.isValidTunnelName(trimmed)) {
      return TunnelSaveResult.INVALID_NAME
    }
    if (conf.isBlank()) {
      return TunnelSaveResult.INVALID_CONF
    }
    val names = catalog.names()
    val sameAsPrevious = !previousName.isNullOrBlank() && previousName == trimmed
    if (trimmed in names && !sameAsPrevious) {
      return TunnelSaveResult.NAME_IN_USE
    }
    val wrote = runCatching { catalog.writeConf(trimmed, conf) }.isSuccess
    if (!wrote) {
      return TunnelSaveResult.WRITE_FAILED
    }
    ssidMigration()
    val renaming = !previousName.isNullOrBlank() && previousName != trimmed
    val previousWasUp = renaming && tunnelState.isUp(previousName)
    if (renaming) {
      if (previousWasUp) {
        applyRunner.force(TunnelCommand.DOWN, "tunnel-rename", previousName)
      }
      splitTunnels.write(trimmed, splitTunnels.read(previousName))
      splitTunnels.delete(previousName)
      excludedSsids.write(trimmed, excludedSsids.read(previousName))
      excludedSsids.delete(previousName)
      catalog.delete(previousName)
      val current = resolver.persistResolved()
      if (current.tunnelName == previousName || current.mobileTunnelName == previousName) {
        store.write(
          current.copy(
            tunnelName = if (current.tunnelName == previousName) trimmed else current.tunnelName,
            mobileTunnelName = if (current.mobileTunnelName == previousName) trimmed else current.mobileTunnelName,
          ),
        )
      }
    }
    val current = resolver.persistResolved()
    if (current.tunnelName.isBlank()) {
      assignMobileIfBlank(trimmed)
      selectImportedTunnel(trimmed)
      return TunnelSaveResult.SAVED
    }
    if (current.tunnelName != trimmed && current.mobileTunnelName != trimmed) {
      return TunnelSaveResult.SAVED
    }
    applySavedTunnel(trimmed, "tunnel-save", previousWasUp || tunnelState.isUp(trimmed))
    return TunnelSaveResult.SAVED
  }

  fun reloadImported(imported: List<String>) {
    if (imported.isEmpty()) {
      return
    }
    ssidMigration()
    val current = resolver.persistResolved()
    if (current.tunnelName.isBlank()) {
      assignMobileIfBlank(imported.first())
      selectImportedTunnel(imported.first())
      return
    }
    val importedMobile = current.mobileTunnelName.isNotBlank() && current.mobileTunnelName in imported
    if (current.tunnelName !in imported && !importedMobile) {
      return
    }
    val liveName = if (importedMobile && tunnelState.isUp(current.mobileTunnelName)) {
      current.mobileTunnelName
    } else {
      current.tunnelName
    }
    applySavedTunnel(liveName, "tunnel-import", tunnelState.isUp(liveName))
  }

  fun deleteImportedTunnel(name: String) {
    if (name !in catalog.names()) {
      return
    }
    if (tunnelState.isUp(name)) {
      applyRunner.force(TunnelCommand.DOWN, "tunnel-delete", name)
    }
    catalog.delete(name)
    splitTunnels.delete(name)
    excludedSsids.delete(name)
    val current = resolver.persistResolved()
    val clearedMobile = if (current.mobileTunnelName == name) {
      current.copy(mobileTunnelName = "")
    } else {
      current
    }
    if (clearedMobile != current) {
      store.write(clearedMobile)
    }
    if (clearedMobile.tunnelName != name) {
      return
    }
    val remaining = catalog.names()
    if (remaining.isEmpty()) {
      store.write(clearedMobile.copy(tunnelName = "", enabled = false, pausedUntilEpochMillis = null))
      pauseAlarms.cancel()
      return
    }
    selectImportedTunnel(remaining.first())
  }

  private fun assignMobileIfBlank(name: String) {
    val current = resolver.persistResolved()
    if (current.mobileTunnelName.isNotBlank() || name !in catalog.names()) {
      return
    }
    store.write(current.copy(mobileTunnelName = name))
  }

  private fun applySavedTunnel(tunnelName: String, trigger: String, keepUp: Boolean) {
    val resolved = resolver.persistResolved()
    if (resolved.enabled) {
      applyRunner.applyNow(trigger)
      if (keepUp && PolicyEvaluator.decide(resolved, network()) is PolicyDecision.Skip) {
        applyRunner.force(TunnelCommand.UP, trigger, tunnelName)
      }
    } else if (keepUp) {
      applyRunner.force(TunnelCommand.UP, trigger, tunnelName)
    }
  }
}
