package com.wirepilot.app.control

class BootCoordinator(
  private val registerNetworkWatcher: () -> Unit,
  private val reschedulePause: () -> Unit,
  private val scheduleDebouncedApply: () -> Unit,
) {
  fun onBootOrUpdate() {
    registerNetworkWatcher()
    reschedulePause()
    scheduleDebouncedApply()
  }
}
