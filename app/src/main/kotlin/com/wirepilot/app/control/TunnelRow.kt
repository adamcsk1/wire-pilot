package com.wirepilot.app.control

import com.wirepilot.app.data.SplitTunnelMode

data class TunnelRow(
  val name: String,
  val selected: Boolean,
  val up: Boolean,
  val splitMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
  val splitAppCount: Int = 0,
  val excludedSsidCount: Int = 0,
  val mobile: Boolean = false,
)
