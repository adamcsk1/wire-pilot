package com.wirepilot.app.control

enum class SplitTunnelMode {
  ALL_APPS,
  EXCLUDE_APPS,
  INCLUDE_APPS,
}

data class SplitTunnelSelection(
  val excludedPackages: Set<String>,
  val includedPackages: Set<String>,
)

object SplitTunnelPolicy {
  fun selection(mode: SplitTunnelMode, packages: Set<String>): SplitTunnelSelection {
    val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return when (mode) {
      SplitTunnelMode.ALL_APPS -> SplitTunnelSelection(emptySet(), emptySet())
      SplitTunnelMode.EXCLUDE_APPS -> SplitTunnelSelection(cleaned, emptySet())
      SplitTunnelMode.INCLUDE_APPS -> SplitTunnelSelection(emptySet(), cleaned)
    }
  }

  fun modeFrom(excludedPackages: Set<String>, includedPackages: Set<String>): SplitTunnelMode {
    if (includedPackages.isNotEmpty()) {
      return SplitTunnelMode.INCLUDE_APPS
    }
    if (excludedPackages.isNotEmpty()) {
      return SplitTunnelMode.EXCLUDE_APPS
    }
    return SplitTunnelMode.ALL_APPS
  }
}
