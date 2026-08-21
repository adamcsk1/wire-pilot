package com.wirepilot.app.platform

import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.control.NoOpDiagnosticLog
import com.wirepilot.app.control.TunnelCommand
import com.wirepilot.app.control.TunnelCommands
import com.wirepilot.app.control.TunnelStatePort
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.TunnelCatalog
import android.os.Handler
import android.os.Looper
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GoTunnelController(
  private val backend: GoBackend,
  private val catalog: TunnelCatalog,
  private val splitTunnels: SplitTunnelStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
) : TunnelCommands, TunnelStatePort {
  private val tunnels = ConcurrentHashMap<String, NamedTunnel>()
  private val lastUpConfDigest = ConcurrentHashMap<String, String>()
  private val pending = ConcurrentHashMap<String, PendingCommand>()
  private val nextGeneration = AtomicLong(0)
  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val settledListeners = CopyOnWriteArraySet<() -> Unit>()

  fun addSettledListener(listener: () -> Unit) {
    settledListeners.add(listener)
  }

  fun removeSettledListener(listener: () -> Unit) {
    settledListeners.remove(listener)
  }

  override fun send(tunnelName: String, command: TunnelCommand) {
    if (tunnelName.isBlank()) {
      return
    }
    val generation = nextGeneration.incrementAndGet()
    pending[tunnelName] = PendingCommand(generation, command == TunnelCommand.UP)
    executor.execute { apply(tunnelName, command, generation) }
  }

  override fun isUp(tunnelName: String): Boolean {
    if (tunnelName.isBlank()) {
      return false
    }
    pending[tunnelName]?.let { return it.wantUp }
    val tunnel = tunnels.getOrPut(tunnelName) { namedTunnel(tunnelName) }
    return runCatching { backend.getState(tunnel) }.getOrDefault(Tunnel.State.DOWN) == Tunnel.State.UP
  }

  private fun apply(tunnelName: String, command: TunnelCommand, generation: Long) {
    try {
      applyCommand(tunnelName, command, generation)
    } finally {
      notifySettled()
    }
  }

  private fun applyCommand(tunnelName: String, command: TunnelCommand, generation: Long) {
    val stored = catalog.readConf(tunnelName)
    if (stored == null) {
      if (command == TunnelCommand.DOWN) {
        downMissingConf(tunnelName, generation)
      } else {
        clearPendingIfCurrent(tunnelName, generation)
        log.record(LogKind.TUNNEL_ERROR, "missing-conf tunnel=$tunnelName")
      }
      return
    }
    val parsed = ConfigZipIO.parseOrNull(stored)
    if (parsed == null) {
      clearPendingIfCurrent(tunnelName, generation)
      log.record(LogKind.TUNNEL_ERROR, "bad-conf tunnel=$tunnelName")
      return
    }
    val merged = ConfigSplitMerger.merge(parsed, splitTunnels.read(tunnelName))
    val tunnel = tunnels.getOrPut(tunnelName) { namedTunnel(tunnelName) }
    val desired = if (command == TunnelCommand.UP) Tunnel.State.UP else Tunnel.State.DOWN
    val actual = runCatching { backend.getState(tunnel) }.getOrDefault(Tunnel.State.DOWN)
    val confDigest = digest(ConfigSplitMerger.toConf(merged))
    downOtherTunnels(keepName = if (command == TunnelCommand.UP) tunnelName else null)
    if (desired == Tunnel.State.DOWN && actual == Tunnel.State.DOWN) {
      clearPendingIfCurrent(tunnelName, generation)
      return
    }
    if (desired == Tunnel.State.UP && actual == Tunnel.State.UP && lastUpConfDigest[tunnelName] == confDigest) {
      clearPendingIfCurrent(tunnelName, generation)
      return
    }
    val config = if (command == TunnelCommand.UP) merged else null
    runCatching { backend.setState(tunnel, desired, config) }
      .onSuccess { state ->
        if (state == Tunnel.State.UP) {
          lastUpConfDigest[tunnelName] = confDigest
        } else {
          lastUpConfDigest.remove(tunnelName)
        }
        clearPendingIfCurrent(tunnelName, generation)
        log.record(LogKind.TUNNEL, "state=${state.name} tunnel=$tunnelName")
      }
      .onFailure { error ->
        clearPendingIfCurrent(tunnelName, generation)
        log.record(LogKind.TUNNEL_ERROR, "${error.javaClass.simpleName} tunnel=$tunnelName")
      }
  }

  private fun downMissingConf(tunnelName: String, generation: Long) {
    val tunnel = tunnels[tunnelName]
    if (tunnel == null) {
      lastUpConfDigest.remove(tunnelName)
      clearPendingIfCurrent(tunnelName, generation)
      return
    }
    val actual = runCatching { backend.getState(tunnel) }.getOrDefault(Tunnel.State.DOWN)
    if (actual == Tunnel.State.DOWN) {
      lastUpConfDigest.remove(tunnelName)
      clearPendingIfCurrent(tunnelName, generation)
      return
    }
    runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
      .onSuccess { state ->
        lastUpConfDigest.remove(tunnelName)
        clearPendingIfCurrent(tunnelName, generation)
        log.record(LogKind.TUNNEL, "state=${state.name} tunnel=$tunnelName")
      }
      .onFailure { error ->
        clearPendingIfCurrent(tunnelName, generation)
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
          lastUpConfDigest.remove(name)
          log.record(LogKind.TUNNEL, "state=DOWN tunnel=$name")
        }
        .onFailure { error ->
          log.record(LogKind.TUNNEL_ERROR, "${error.javaClass.simpleName} tunnel=$name")
        }
    }
  }

  private fun clearPendingIfCurrent(tunnelName: String, generation: Long) {
    pending.computeIfPresent(tunnelName) { _, current ->
      if (current.generation == generation) null else current
    }
  }

  private fun namedTunnel(tunnelName: String): NamedTunnel {
    return NamedTunnel(tunnelName) { state -> onExternalState(tunnelName, state) }
  }

  private fun onExternalState(tunnelName: String, newState: Tunnel.State) {
    if (newState == Tunnel.State.DOWN) {
      lastUpConfDigest.remove(tunnelName)
    }
    notifySettled()
  }

  private fun notifySettled() {
    if (settledListeners.isEmpty()) {
      return
    }
    mainHandler.post {
      settledListeners.forEach { listener -> listener() }
    }
  }

  private fun digest(confText: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(confText.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
  }
}

private data class PendingCommand(
  val generation: Long,
  val wantUp: Boolean,
)

class NamedTunnel(
  private val tunnelName: String,
  private val onChanged: (Tunnel.State) -> Unit = {},
) : Tunnel {
  override fun getName(): String = tunnelName

  override fun onStateChange(newState: Tunnel.State) = onChanged(newState)
}
