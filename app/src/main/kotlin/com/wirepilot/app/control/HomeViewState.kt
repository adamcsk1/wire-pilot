package com.wirepilot.app.control

data class HomeViewState(
  val tunnelName: String,
  val importedTunnels: List<String> = emptyList(),
  val tunnelRows: List<TunnelRow> = emptyList(),
  val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
  val splitTunnelPackages: Set<String> = emptySet(),
  val excludedSsids: List<String>,
  val status: StatusPresentation,
  val applyNow: ApplyNowView,
  val loggingEnabled: Boolean,
  val policyLoggingEnabled: Boolean = true,
  val vpnLoggingEnabled: Boolean = true,
  val logPreview: String,
  val policyLogText: String = "",
  val vpnLogText: String = "",
  val logCopyText: String,
  val connectOnMobile: Boolean,
  val mobileTunnelName: String = "",
  val controlSelection: ControlSelection,
  val vpnConnected: Boolean = false,
)
