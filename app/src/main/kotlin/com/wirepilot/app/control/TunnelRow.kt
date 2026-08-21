package com.wirepilot.app.control

data class TunnelRow(
  val name: String,
  val selected: Boolean,
  val up: Boolean,
  val splitMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
  val splitAppCount: Int = 0,
  val excludedSsidCount: Int = 0,
  val mobile: Boolean = false,
)
