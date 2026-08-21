package com.wirepilot.app.data

data class SsidEncryptionPlan(
  val toCopy: Map<String, String>,
  val toDelete: Set<String>,
)

object SsidEncryptionMigrator {
  fun keysToCopy(
    allPlainKeys: Set<String>,
    lastKnownKey: String,
    excludedPrefix: String,
  ): Set<String> {
    return allPlainKeys.filter { key ->
      key == lastKnownKey || key.startsWith(excludedPrefix)
    }.toSet()
  }

  fun plan(
    plainEntries: Map<String, String?>,
    encryptedKeys: Set<String>,
    lastKnownKey: String,
    excludedPrefix: String,
  ): SsidEncryptionPlan {
    val candidates = keysToCopy(plainEntries.keys, lastKnownKey, excludedPrefix)
    val toCopy = linkedMapOf<String, String>()
    val toDelete = mutableSetOf<String>()
    candidates.forEach { key ->
      if (key in encryptedKeys) {
        toDelete += key
        return@forEach
      }
      val value = plainEntries[key]
      if (!value.isNullOrEmpty()) {
        toCopy[key] = value
      }
    }
    return SsidEncryptionPlan(toCopy = toCopy, toDelete = toDelete)
  }

  fun deletesAfterCopy(plan: SsidEncryptionPlan, copiedKeys: Set<String>): Set<String> {
    return plan.toDelete + copiedKeys
  }
}
