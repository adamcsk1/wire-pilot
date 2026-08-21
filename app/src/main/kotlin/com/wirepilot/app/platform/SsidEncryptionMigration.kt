package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.SsidEncryptionMigrator

object SsidEncryptionMigration {
  fun run(plain: SharedPreferences, encrypted: SharedPreferences) {
    val plan = SsidEncryptionMigrator.plan(
      plainEntries = plain.all.mapValues { entry -> entry.value as? String },
      encryptedKeys = encrypted.all.keys.filterNotNull().toSet(),
      lastKnownKey = PreferenceKeys.LAST_KNOWN_SSID,
      excludedPrefix = PreferenceKeys.EXCLUDED_SSID_PREFIX,
    )
    if (plan.toCopy.isEmpty() && plan.toDelete.isEmpty()) {
      return
    }
    if (plan.toCopy.isNotEmpty()) {
      encrypted.edit(commit = true) {
        plan.toCopy.forEach { (key, value) ->
          putString(key, value)
        }
      }
    }
    val copied = plan.toCopy.keys.filter { key -> encrypted.contains(key) }.toSet()
    val deletes = SsidEncryptionMigrator.deletesAfterCopy(plan, copied)
    if (deletes.isEmpty()) {
      return
    }
    plain.edit(commit = true) {
      deletes.forEach { key -> remove(key) }
    }
  }
}
