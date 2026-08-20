package com.wirepilot.app.control

data class HomeViewState(
  val tunnelName: String,
  val excludedSsids: List<String>,
  val status: StatusPresentation,
  val applyNow: ApplyNowView,
  val loggingEnabled: Boolean,
  val logPreview: String,
  val logCopyText: String,
  val connectOnMobile: Boolean,
  val controlSelection: ControlSelection,
)
