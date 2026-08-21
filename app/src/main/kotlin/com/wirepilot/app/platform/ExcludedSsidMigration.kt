package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.control.ExcludedSsidMigrator
import com.wirepilot.app.data.ControlCodec
import com.wirepilot.app.data.ExcludedSsidStore
import com.wirepilot.app.data.TunnelCatalog

object ExcludedSsidMigration {
  fun run(
    preferences: SharedPreferences,
    catalog: TunnelCatalog,
    store: ExcludedSsidStore,
  ) {
    val already = preferences.getBoolean(PreferenceKeys.EXCLUDED_SSIDS_MIGRATED, false)
    val global = ControlCodec.decodeSsids(preferences.getString(PreferenceKeys.EXCLUDED_SSIDS, ""))
    val result = ExcludedSsidMigrator.migrate(already, global, catalog.names(), store)
    if (result.migrated && !already) {
      preferences.edit {
        putBoolean(PreferenceKeys.EXCLUDED_SSIDS_MIGRATED, true)
        remove(PreferenceKeys.EXCLUDED_SSIDS)
      }
    }
  }
}
