package com.wirepilot.app.control

import com.wirepilot.app.support.InMemoryExcludedSsidStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExcludedSsidMigratorTest {
  @Test
  fun alreadyMigratedIsNoOp() {
    val store = InMemoryExcludedSsidStore()
    val result = ExcludedSsidMigrator.migrate(true, setOf("Home"), listOf("office"), store)
    assertTrue(result.migrated)
    assertFalse(result.copied)
    assertEquals(emptySet(), store.read("office"))
  }

  @Test
  fun waitsWhenGlobalExistsAndNoTunnels() {
    val store = InMemoryExcludedSsidStore()
    val result = ExcludedSsidMigrator.migrate(false, setOf("Home"), emptyList(), store)
    assertFalse(result.migrated)
    assertFalse(result.copied)
  }

  @Test
  fun copiesGlobalOntoEveryTunnel() {
    val store = InMemoryExcludedSsidStore()
    val result = ExcludedSsidMigrator.migrate(false, setOf("Home"), listOf("office", "HomeVPN"), store)
    assertTrue(result.migrated)
    assertTrue(result.copied)
    assertEquals(setOf("Home"), store.read("office"))
    assertEquals(setOf("Home"), store.read("HomeVPN"))
  }

  @Test
  fun doesNotOverwriteExistingList() {
    val store = InMemoryExcludedSsidStore()
    store.write("office", setOf("Cafe"))
    val result = ExcludedSsidMigrator.migrate(false, setOf("Home"), listOf("office", "HomeVPN"), store)
    assertTrue(result.migrated)
    assertTrue(result.copied)
    assertEquals(setOf("Cafe"), store.read("office"))
    assertEquals(setOf("Home"), store.read("HomeVPN"))
  }

  @Test
  fun emptyGlobalWithNoTunnelsStillMigrates() {
    val store = InMemoryExcludedSsidStore()
    val result = ExcludedSsidMigrator.migrate(false, emptySet(), emptyList(), store)
    assertTrue(result.migrated)
    assertFalse(result.copied)
  }
}
