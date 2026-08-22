package com.wirepilot.app.control

enum class NetworkMonitorServiceStart {
  STICKY,
  NOT_STICKY,
}

class NetworkMonitorServiceRuntime(
  private val registerFallbacks: () -> Unit,
  private val unregisterFallbacks: () -> Unit,
  private val startLive: () -> Unit,
  private val updateNotification: (NetworkMonitorMode) -> Unit,
  private val stopService: (Int) -> Unit,
) {
  fun onStart(mode: NetworkMonitorMode, startId: Int): NetworkMonitorServiceStart {
    if (mode == NetworkMonitorMode.STOPPED) {
      unregisterFallbacks()
      stopService(startId)
      return NetworkMonitorServiceStart.NOT_STICKY
    }
    registerFallbacks()
    startLive()
    updateNotification(mode)
    return NetworkMonitorServiceStart.STICKY
  }
}
