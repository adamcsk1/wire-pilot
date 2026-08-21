package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.support.InMemoryControlStore
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkMonitorCoordinatorTest {
  private val now = 10_000L

  @Test
  fun enabledSelectedTunnelWatches() {
    assertEquals(
      NetworkMonitorMode.WATCHING,
      NetworkMonitorPolicy.mode(StoredControl(tunnelName = "office"), now),
    )
  }

  @Test
  fun activeTimedPauseKeepsPausedMonitor() {
    assertEquals(
      NetworkMonitorMode.PAUSED,
      NetworkMonitorPolicy.mode(
        StoredControl(enabled = false, pausedUntilEpochMillis = now + 1L, tunnelName = "office"),
        now,
      ),
    )
  }

  @Test
  fun expiredPauseWatchesAgain() {
    assertEquals(
      NetworkMonitorMode.WATCHING,
      NetworkMonitorPolicy.mode(
        StoredControl(enabled = false, pausedUntilEpochMillis = now, tunnelName = "office"),
        now,
      ),
    )
  }

  @Test
  fun permanentOffStopsMonitor() {
    assertEquals(
      NetworkMonitorMode.STOPPED,
      NetworkMonitorPolicy.mode(StoredControl(enabled = false, tunnelName = "office"), now),
    )
  }

  @Test
  fun blankTunnelStopsMonitor() {
    assertEquals(
      NetworkMonitorMode.STOPPED,
      NetworkMonitorPolicy.mode(StoredControl(tunnelName = ""), now),
    )
  }

  @Test
  fun blankTunnelDuringTimedPauseStopsMonitor() {
    assertEquals(
      NetworkMonitorMode.STOPPED,
      NetworkMonitorPolicy.mode(
        StoredControl(enabled = false, pausedUntilEpochMillis = now + 1L),
        now,
      ),
    )
  }

  @Test
  fun enabledStateWithStalePauseWatches() {
    assertEquals(
      NetworkMonitorMode.WATCHING,
      NetworkMonitorPolicy.mode(
        StoredControl(enabled = true, pausedUntilEpochMillis = now + 1L, tunnelName = "office"),
        now,
      ),
    )
  }

  @Test
  fun reconcilePersistsExpiredPauseAndAppliesMode() {
    val store = InMemoryControlStore(
      StoredControl(enabled = false, pausedUntilEpochMillis = now, tunnelName = "office"),
    )
    val applied = mutableListOf<NetworkMonitorMode>()
    val serviceStartAllowed = mutableListOf<Boolean>()
    val coordinator = NetworkMonitorCoordinator(store, { now }) { mode, allowServiceStart ->
      applied += mode
      serviceStartAllowed += allowServiceStart
    }

    coordinator.reconcile()

    assertEquals(StoredControl(enabled = true, tunnelName = "office"), store.read())
    assertEquals(listOf(NetworkMonitorMode.WATCHING), applied)
    assertEquals(listOf(true), serviceStartAllowed)
  }

  @Test
  fun currentModeLeavesResolvedStateUnchanged() {
    val initial = StoredControl(tunnelName = "office")
    val store = CountingControlStore(initial)
    val coordinator = NetworkMonitorCoordinator(store, { now }) { _, _ -> }

    assertEquals(NetworkMonitorMode.WATCHING, coordinator.currentMode())
    assertEquals(initial, store.read())
    assertEquals(0, store.writeCount)
  }

  @Test
  fun reconcileWithoutServiceStartAppliesModeAsFallbackOnly() {
    val store = InMemoryControlStore(StoredControl(tunnelName = "office"))
    val applied = mutableListOf<Pair<NetworkMonitorMode, Boolean>>()
    val coordinator = NetworkMonitorCoordinator(store, { now }) { mode, allowServiceStart ->
      applied += mode to allowServiceStart
    }

    coordinator.reconcileWithoutServiceStart()

    assertEquals(listOf(NetworkMonitorMode.WATCHING to false), applied)
  }

  private class CountingControlStore(initial: StoredControl) : ControlStore {
    private var control = initial
    var writeCount = 0
      private set

    override fun read(): StoredControl = control

    override fun write(control: StoredControl) {
      this.control = control
      writeCount += 1
    }
  }
}
