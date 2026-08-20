package com.wirepilot.app.control

class PauseExpiryCoordinator(
  private val applyRunner: ApplyRunner,
  private val onApplied: () -> Unit = {},
) {
  fun onPauseExpired() {
    applyRunner.applyNow("pause-expiry")
    onApplied()
  }
}
