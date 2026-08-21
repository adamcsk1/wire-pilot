package com.wirepilot.app.control

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.StoredControl

class ControlResolver(
  private val store: ControlStore,
  private val clock: () -> Long,
) {
  fun persistResolved(): StoredControl {
    val stored = store.read()
    val resolved = ControlModeResolver.resolve(stored, clock())
    if (resolved != stored) {
      store.write(resolved)
    }
    return resolved
  }
}
