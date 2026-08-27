package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlEnabledMigratorTest {
  @Test
  fun writtenKeyWins() {
    assertTrue(
      ControlEnabledMigrator.enabled(
        hasEnabledKey = true,
        storedEnabled = true,
        hasPriorControlState = false,
      ),
    )
    assertFalse(
      ControlEnabledMigrator.enabled(
        hasEnabledKey = true,
        storedEnabled = false,
        hasPriorControlState = true,
      ),
    )
  }

  @Test
  fun missingKeyKeepsOldInstallOn() {
    assertTrue(
      ControlEnabledMigrator.enabled(
        hasEnabledKey = false,
        storedEnabled = false,
        hasPriorControlState = true,
      ),
    )
  }

  @Test
  fun missingKeyLeavesFreshInstallOff() {
    assertFalse(
      ControlEnabledMigrator.enabled(
        hasEnabledKey = false,
        storedEnabled = false,
        hasPriorControlState = false,
      ),
    )
  }
}
