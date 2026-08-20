package com.wirepilot.app.control

class NetworkChangeCoordinator(
  private val scheduleDebouncedApply: () -> Unit,
) {
  fun onNetworkChanged() {
    scheduleDebouncedApply()
  }
}
