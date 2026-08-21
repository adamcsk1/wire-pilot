package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkMonitorRuntimeTest {
  @Test
  fun watchingAndPausedRegisterFallbacksAndStartService() {
    val events = mutableListOf<String>()
    val runtime = runtime(events)

    runtime.apply(NetworkMonitorMode.WATCHING)
    runtime.apply(NetworkMonitorMode.PAUSED)

    assertEquals(listOf("register", "start", "register", "start"), events)
  }

  @Test
  fun stoppedUnregistersImmediatelyAndStopsWhenTunnelIsIdle() {
    val events = mutableListOf<String>()
    var onIdle: (() -> Unit)? = null
    val runtime = runtime(events) { action -> onIdle = action }

    runtime.apply(NetworkMonitorMode.STOPPED)
    assertEquals(listOf("unregister"), events)

    onIdle?.invoke()
    assertEquals(listOf("unregister", "stop"), events)
  }

  @Test
  fun activeRequestCancelsPendingStop() {
    val events = mutableListOf<String>()
    var onIdle: (() -> Unit)? = null
    val runtime = runtime(events) { action -> onIdle = action }

    runtime.apply(NetworkMonitorMode.STOPPED)
    runtime.apply(NetworkMonitorMode.WATCHING)
    onIdle?.invoke()

    assertEquals(listOf("unregister", "register", "start"), events)
  }

  @Test
  fun fallbackOnlyActiveRequestDoesNotStartService() {
    val events = mutableListOf<String>()
    val runtime = runtime(events)

    runtime.apply(NetworkMonitorMode.WATCHING, allowServiceStart = false)

    assertEquals(listOf("register"), events)
  }

  private fun runtime(
    events: MutableList<String>,
    whenTunnelIdle: (() -> Unit) -> Unit = { action -> action() },
  ): NetworkMonitorRuntime {
    return NetworkMonitorRuntime(
      registerFallbacks = { events += "register" },
      unregisterFallbacks = { events += "unregister" },
      startService = { events += "start" },
      stopService = { events += "stop" },
      whenTunnelIdle = whenTunnelIdle,
    )
  }
}
