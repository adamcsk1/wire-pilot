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
    var registered = false
    var rescheduled = false
    var scheduled = false
    BootCoordinator(
      registerNetworkWatcher = { registered = true },
      reschedulePause = { rescheduled = true },
      scheduleDebouncedApply = { scheduled = true },
    ).onBootOrUpdate()
    assertTrue(registered)
    assertTrue(rescheduled)
    assertTrue(scheduled)
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
    PauseExpiryCoordinator(runner).onPauseExpired()
    assertEquals(listOf("office" to TunnelCommand.UP), tunnel.commands)
    assertEquals(true, store.read().enabled)
  }
}
