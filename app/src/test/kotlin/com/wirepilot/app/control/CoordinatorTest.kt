package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinatorTest {
  @Test
  fun bootRegistersAndSchedulesDebounce() {
    val events = mutableListOf<String>()
    BootCoordinator(
      reconcileNetworkMonitor = { events += "reconcile" },
      reschedulePause = { events += "reschedule" },
      scheduleDebouncedApply = { events += "schedule" },
    ).onBootOrUpdate()
    assertEquals(listOf("reconcile", "reschedule", "schedule"), events)
  }

  @Test
  fun networkChangeSchedulesDebounce() {
    var scheduled = false
    NetworkChangeCoordinator(scheduleDebouncedApply = { scheduled = true }).onNetworkChanged()
    assertTrue(scheduled)
  }

  @Test
  fun pauseExpiryAppliesImmediately() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = 1L, tunnelName = "office", mobileTunnelName = "office"),
    )
    val tunnel = RecordingTunnel()
    val runner = ApplyRunner(
      store = store,
      clock = { 10L },
      network = { NetworkSnapshot(NetworkKind.MOBILE) },
      tunnel = tunnel,
    )
    var enabledWhenReconciled = false
    PauseExpiryCoordinator(runner, onApplied = { enabledWhenReconciled = store.read().enabled }).onPauseExpired()
    assertEquals(listOf("office" to TunnelCommand.UP), tunnel.commands)
    assertEquals(true, store.read().enabled)
    assertTrue(enabledWhenReconciled)
  }
}
