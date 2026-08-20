package com.wirepilot.app.platform

import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.control.NoOpDiagnosticLog
import com.wirepilot.app.control.TunnelCommand
import com.wirepilot.app.control.TunnelCommands
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.TunnelCatalog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class GoTunnelController(
  private val backend: GoBackend,
  private val catalog: TunnelCatalog,
  private val splitTunnels: SplitTunnelStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
) : TunnelCommands {
  private val tunnels = ConcurrentHashMap<String, NamedTunnel>()
  private val lastUpConf = ConcurrentHashMap<String, String>()
  private val executor = Executors.newSingleThreadExecutor()

  override fun send(tunnelName: String, command: TunnelCommand) {
    if (tunnelName.isBlank()) {
      return
    }
    executor.execute { apply(tunnelName, command) }
  }

  private fun apply(tunnelName: String, command: TunnelCommand) {
    val stored = catalog.readConf(tunnelName)
    if (stored == null) {
      log.record(LogKind.TUNNEL_ERROR, "missing-conf tunnel=$tunnelName")
      return
    }
    val parsed = ConfigZipIO.parseOrNull(stored)
    if (parsed == null) {
      log.record(LogKind.TUNNEL_ERROR, "bad-conf tunnel=$tunnelName")
      return
    }
    val merged = ConfigSplitMerger.merge(parsed, splitTunnels.read(tunnelName))
    val tunnel = tunnels.getOrPut(tunnelName) { NamedTunnel(tunnelName) }
    val desired = if (command == TunnelCommand.UP) Tunnel.State.UP else Tunnel.State.DOWN
    val actual = runCatching { backend.getState(tunnel) }.getOrDefault(Tunnel.State.DOWN)
    val confText = ConfigSplitMerger.toConf(merged)
    downOtherTunnels(keepName = if (command == TunnelCommand.UP) tunnelName else null)
    if (desired == Tunnel.State.DOWN && actual == Tunnel.State.DOWN) {
      return
    }
    if (desired == Tunnel.State.UP && actual == Tunnel.State.UP && lastUpConf[tunnelName] == confText) {
      return
    }
    val config = if (command == TunnelCommand.UP) merged else null
    runCatching { backend.setState(tunnel, desired, config) }
      .onSuccess { state ->
        if (state == Tunnel.State.UP) {
          lastUpConf[tunnelName] = confText
        } else {
          lastUpConf.remove(tunnelName)
        }
        log.record(LogKind.TUNNEL, "state=${state.name} tunnel=$tunnelName")
      }
      .onFailure { error ->
        log.record(LogKind.TUNNEL_ERROR, "${error.javaClass.simpleName} tunnel=$tunnelName")
      }
  }

  private fun downOtherTunnels(keepName: String?) {
    tunnels.forEach { (name, other) ->
      if (keepName != null && name == keepName) {
        return@forEach
      }
      val state = runCatching { backend.getState(other) }.getOrDefault(Tunnel.State.DOWN)
      if (state != Tunnel.State.UP) {
        return@forEach
      }
      runCatching { backend.setState(other, Tunnel.State.DOWN, null) }
        .onSuccess {
          lastUpConf.remove(name)
          log.record(LogKind.TUNNEL, "state=DOWN tunnel=$name")
        }
        .onFailure { error ->
          log.record(LogKind.TUNNEL_ERROR, "${error.javaClass.simpleName} tunnel=$name")
        }
    }
  }
}

class NamedTunnel(
  private val tunnelName: String,
) : Tunnel {
  override fun getName(): String = tunnelName

  override fun onStateChange(newState: Tunnel.State) = Unit
}
