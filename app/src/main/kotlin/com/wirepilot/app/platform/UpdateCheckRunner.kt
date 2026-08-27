package com.wirepilot.app.platform

import com.wirepilot.app.control.UpdateCheckCoordinator
import com.wirepilot.app.control.UpdateCheckDecision
import java.util.concurrent.Executors

class UpdateCheckRunner(
  private val coordinator: UpdateCheckCoordinator,
  private val cancelFetch: () -> Unit,
) {
  private val executor = Executors.newSingleThreadExecutor()

  fun checkNow(onDone: (UpdateCheckDecision) -> Unit) {
    executor.execute {
      onDone(coordinator.checkNow())
    }
  }

  fun runPeriodic(onDone: () -> Unit): () -> Unit {
    executor.execute {
      try {
        coordinator.onPeriodicCheck()
      } finally {
        onDone()
      }
    }
    return cancelFetch
  }

  fun reschedule() {
    coordinator.reschedule()
  }

  fun setNotifyEnabled(enabled: Boolean) {
    coordinator.setNotifyEnabled(enabled)
  }
}
