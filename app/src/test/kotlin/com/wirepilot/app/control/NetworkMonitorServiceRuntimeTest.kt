package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkMonitorServiceRuntimeTest {
  @Test
  fun stoppedModeCleansFallbacksAndStopsWithoutRestart() {
    val events = mutableListOf<String>()
    val runtime = runtime(events)

    val result = runtime.onStart(NetworkMonitorMode.STOPPED, startId = 7)

    assertEquals(NetworkMonitorServiceStart.NOT_STICKY, result)
    assertEquals(listOf("unregister", "stop:7"), events)
  }

  @Test
  fun watchingAndPausedModesStartLiveMonitoringAndStaySticky() {
    val events = mutableListOf<String>()
    val runtime = runtime(events)

    val watching = runtime.onStart(NetworkMonitorMode.WATCHING, startId = 1)
    val paused = runtime.onStart(NetworkMonitorMode.PAUSED, startId = 2)

    assertEquals(NetworkMonitorServiceStart.STICKY, watching)
    assertEquals(NetworkMonitorServiceStart.STICKY, paused)
    assertEquals(
      listOf(
        "register",
        "live-start",
        "notify:WATCHING",
        "register",
        "live-start",
        "notify:PAUSED",
      ),
      events,
    )
  }

  @Test
  fun destroyStopsLiveMonitoring() {
    val events = mutableListOf<String>()
    val runtime = runtime(events)

    runtime.onDestroy()

    assertEquals(listOf("live-stop"), events)
  }

  private fun runtime(events: MutableList<String>): NetworkMonitorServiceRuntime {
    return NetworkMonitorServiceRuntime(
      registerFallbacks = { events += "register" },
      unregisterFallbacks = { events += "unregister" },
      startLive = { events += "live-start" },
      stopLive = { events += "live-stop" },
      updateNotification = { mode -> events += "notify:${mode.name}" },
      stopService = { startId -> events += "stop:$startId" },
    )
  }
}
