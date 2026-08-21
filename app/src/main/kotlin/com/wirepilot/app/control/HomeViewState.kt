package com.wirepilot.app.control

import com.wirepilot.app.data.SplitTunnelMode

data class HomeViewState(
  val tunnelName: String,
  val importedTunnels: List<String> = emptyList(),
  val tunnelRows: List<TunnelRow> = emptyList(),
  val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
  val splitTunnelPackages: Set<String> = emptySet(),
  val excludedSsids: List<String>,
  val status: StatusPresentation,
  val policyLine: PolicyLine,
  val applyNow: ApplyNowView,
  val loggingEnabled: Boolean,
  val policyLoggingEnabled: Boolean = false,
  val vpnLoggingEnabled: Boolean = false,
  val usageEnabled: Boolean = false,
  val logPreview: String,
  val policyLogText: String = "",
  val vpnLogText: String = "",
  val logCopyText: String,
  val connectOnMobile: Boolean,
  val mobileTunnelName: String = "",
  val controlSelection: ControlSelection,
  val vpnConnected: Boolean = false,
)
