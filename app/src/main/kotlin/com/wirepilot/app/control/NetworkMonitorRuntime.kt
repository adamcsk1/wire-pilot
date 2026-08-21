package com.wirepilot.app.control

class NetworkMonitorRuntime(
  private val registerFallbacks: () -> Unit,
  private val unregisterFallbacks: () -> Unit,
  private val startService: () -> Unit,
  private val stopService: () -> Unit,
  private val whenTunnelIdle: (() -> Unit) -> Unit,
) {
  private var requestGeneration = 0L

  @Synchronized
  fun apply(mode: NetworkMonitorMode, allowServiceStart: Boolean = true) {
    requestGeneration += 1L
    val generation = requestGeneration
    if (mode == NetworkMonitorMode.STOPPED) {
      unregisterFallbacks()
      whenTunnelIdle {
        if (isCurrent(generation)) {
          stopService()
        }
      }
      return
    }
    registerFallbacks()
    if (allowServiceStart) {
      startService()
    }
  }

  @Synchronized
  private fun isCurrent(generation: Long): Boolean = generation == requestGeneration
}
