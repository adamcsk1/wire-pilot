package com.wirepilot.app.control

import java.util.concurrent.atomic.AtomicBoolean

class TunnelIdleActions(
  private val isIdle: () -> Boolean,
  private val addSettledListener: (() -> Unit) -> Unit,
  private val removeSettledListener: (() -> Unit) -> Unit,
  private val dispatch: (() -> Unit) -> Unit,
) {
  fun run(action: () -> Unit): () -> Unit {
    val completed = AtomicBoolean(false)
    lateinit var listener: () -> Unit
    listener = {
      if (isIdle() && completed.compareAndSet(false, true)) {
        removeSettledListener(listener)
        action()
      }
    }
    addSettledListener(listener)
    dispatch(listener)
    return {
      if (completed.compareAndSet(false, true)) {
        removeSettledListener(listener)
      }
    }
  }
}
