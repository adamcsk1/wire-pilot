package com.wirepilot.app.control

class BoundedCompletion(
  private val finish: () -> Unit,
  private val scheduleTimeout: (() -> Unit) -> Unit,
) {
  private var completed = false
  private var cancelWork: () -> Unit = {}

  fun arm() {
    scheduleTimeout { complete() }
  }

  fun setCancellation(cancel: () -> Unit) {
    val cancelNow = synchronized(this) {
      if (completed) {
        true
      } else {
        cancelWork = cancel
        false
      }
    }
    if (cancelNow) {
      cancel()
    }
  }

  fun complete() {
    val cancellation = synchronized(this) {
      if (completed) {
        return
      }
      completed = true
      cancelWork
    }
    cancellation()
    finish()
  }
}
