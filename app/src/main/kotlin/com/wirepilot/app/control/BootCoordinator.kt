package com.wirepilot.app.control

class BootCoordinator(
  private val reconcileNetworkMonitor: () -> Unit,
  private val reschedulePause: () -> Unit,
  private val scheduleDebouncedApply: () -> Unit,
  private val rescheduleUpdateCheck: () -> Unit,
) {
  fun onBootOrUpdate() {
    reconcileNetworkMonitor()
    reschedulePause()
    scheduleDebouncedApply()
    rescheduleUpdateCheck()
  }
}
