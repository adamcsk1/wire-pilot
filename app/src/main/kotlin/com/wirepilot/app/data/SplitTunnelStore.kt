package com.wirepilot.app.data

data class StoredSplitTunnel(
  val mode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
  val packages: Set<String> = emptySet(),
)

interface SplitTunnelStore {
  fun read(tunnelName: String): StoredSplitTunnel
  fun write(tunnelName: String, settings: StoredSplitTunnel)
  fun delete(tunnelName: String)
}

object EmptySplitTunnelStore : SplitTunnelStore {
  override fun read(tunnelName: String): StoredSplitTunnel = StoredSplitTunnel()
  override fun write(tunnelName: String, settings: StoredSplitTunnel) = Unit
  override fun delete(tunnelName: String) = Unit
}

object SplitTunnelCodec {
  fun encode(settings: StoredSplitTunnel): String {
    val packages = settings.packages.sorted().joinToString(",")
    return "${settings.mode.name}\t$packages"
  }

  fun decode(raw: String?): StoredSplitTunnel {
    if (raw.isNullOrBlank()) {
      return StoredSplitTunnel()
    }
    val parts = raw.split('\t', limit = 2)
    val mode = runCatching { SplitTunnelMode.valueOf(parts[0]) }.getOrDefault(SplitTunnelMode.ALL_APPS)
    val packages = if (parts.size < 2 || parts[1].isBlank()) {
      emptySet()
    } else {
      parts[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    return StoredSplitTunnel(mode = mode, packages = packages)
  }
}
