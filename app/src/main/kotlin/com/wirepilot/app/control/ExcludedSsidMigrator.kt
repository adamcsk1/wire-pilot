package com.wirepilot.app.control

import com.wirepilot.app.data.ExcludedSsidStore

data class ExcludedSsidMigrateResult(
  val migrated: Boolean,
  val copied: Boolean,
)

object ExcludedSsidMigrator {
  fun migrate(
    alreadyMigrated: Boolean,
    global: Set<String>,
    names: List<String>,
    store: ExcludedSsidStore,
  ): ExcludedSsidMigrateResult {
    if (alreadyMigrated) {
      return ExcludedSsidMigrateResult(migrated = true, copied = false)
    }
    if (names.isEmpty() && global.isNotEmpty()) {
      return ExcludedSsidMigrateResult(migrated = false, copied = false)
    }
    names.forEach { name ->
      if (!store.exists(name)) {
        store.write(name, global)
      }
    }
    return ExcludedSsidMigrateResult(
      migrated = true,
      copied = global.isNotEmpty() && names.isNotEmpty(),
    )
  }
}
