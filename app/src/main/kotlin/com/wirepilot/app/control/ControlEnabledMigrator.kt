package com.wirepilot.app.control

object ControlEnabledMigrator {
  fun enabled(
    hasEnabledKey: Boolean,
    storedEnabled: Boolean,
    hasPriorControlState: Boolean,
  ): Boolean {
    if (hasEnabledKey) {
      return storedEnabled
    }
    return hasPriorControlState
  }
}
