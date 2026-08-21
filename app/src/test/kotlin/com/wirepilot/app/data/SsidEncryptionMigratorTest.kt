package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SsidEncryptionMigratorTest {
  @Test
  fun copiesLastKnownAndExcludedKeysOnly() {
    val keys = SsidEncryptionMigrator.keysToCopy(
      allPlainKeys = setOf(
        "last_known_ssid",
        "excluded_ssids_office",
        "enabled",
        "excluded_ssids",
        "tunnel_name",
      ),
      lastKnownKey = "last_known_ssid",
      excludedPrefix = "excluded_ssids_",
    )
    assertEquals(setOf("last_known_ssid", "excluded_ssids_office"), keys)
  }

  @Test
  fun emptyWhenNoMatchingKeys() {
    assertEquals(
      emptySet(),
      SsidEncryptionMigrator.keysToCopy(
        allPlainKeys = setOf("enabled", "tunnel_name"),
        lastKnownKey = "last_known_ssid",
        excludedPrefix = "excluded_ssids_",
      ),
    )
  }

  @Test
  fun planSkipsAlreadyEncryptedAndQueuesPlaintextDelete() {
    val plan = SsidEncryptionMigrator.plan(
      plainEntries = mapOf(
        "last_known_ssid" to "Home\t1",
        "excluded_ssids_office" to "Cafe",
        "enabled" to "1",
      ),
      encryptedKeys = setOf("last_known_ssid"),
      lastKnownKey = "last_known_ssid",
      excludedPrefix = "excluded_ssids_",
    )
    assertEquals(mapOf("excluded_ssids_office" to "Cafe"), plan.toCopy)
    assertEquals(setOf("last_known_ssid"), plan.toDelete)
  }

  @Test
  fun planIgnoresBlankPlainValues() {
    val plan = SsidEncryptionMigrator.plan(
      plainEntries = mapOf("last_known_ssid" to "", "excluded_ssids_office" to null),
      encryptedKeys = emptySet(),
      lastKnownKey = "last_known_ssid",
      excludedPrefix = "excluded_ssids_",
    )
    assertTrue(plan.toCopy.isEmpty())
    assertTrue(plan.toDelete.isEmpty())
  }

  @Test
  fun deletesAfterCopyOnlyRemovesSuccessfulCopies() {
    val plan = SsidEncryptionPlan(
      toCopy = mapOf("excluded_ssids_office" to "Cafe", "last_known_ssid" to "Home"),
      toDelete = setOf("already"),
    )
    assertEquals(
      setOf("already", "excluded_ssids_office"),
      SsidEncryptionMigrator.deletesAfterCopy(plan, setOf("excluded_ssids_office")),
    )
  }
}
