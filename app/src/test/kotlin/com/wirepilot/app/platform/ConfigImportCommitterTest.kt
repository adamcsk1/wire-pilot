package com.wirepilot.app.platform

import com.wirepilot.app.data.ConfigImportBatch
import com.wirepilot.app.data.ImportedTunnel
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog
import com.wirepilot.app.support.InMemoryTunnelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigImportCommitterTest {
  @Test
  fun attemptsEverySnapshotAfterRollbackFailures() {
    val backing = InMemoryTunnelCatalog(mapOf("office" to "old-office", "home" to "old-home", "last" to "old-last"))
    val restoreAttempts = mutableListOf<String>()
    val catalog = object : TunnelCatalog by backing {
      override fun writeConf(name: String, conf: String) {
        if (conf.startsWith("old-")) {
          restoreAttempts += name
          if (name != "last") throw IllegalArgumentException("restore $name")
        }
        backing.writeConf(name, conf)
      }
    }
    val split = FailingSplitStore().apply { failName = "travel" }
    val originalSplit = StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("old.app"))
    split.write("office", originalSplit)
    val batch = ConfigImportBatch(listOf("office", "home", "last", "travel").map { name ->
      ImportedTunnel(name, "new-$name", StoredSplitTunnel())
    })

    val failure = assertFailsWith<IllegalStateException> { ConfigImportCommitter(catalog, split).commit(batch) }

    assertEquals(listOf("office", "home", "last"), restoreAttempts)
    assertEquals("old-last", backing.readConf("last"))
    assertEquals(null, backing.readConf("travel"))
    assertEquals(originalSplit, split.read("office"))
    assertEquals(listOf("restore office", "restore home"), failure.suppressed.map { error -> error.message })
  }

  @Test
  fun cancellationRestoresExistingAndRemovesNewTunnels() {
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old-office"))
    val split = FailingSplitStore()
    val original = StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("old.app"))
    split.write("office", original)
    val batch = ConfigImportBatch(listOf("office", "travel", "last").map { name ->
      ImportedTunnel(name, "new-$name", StoredSplitTunnel())
    })
    var checks = 0

    val result = ConfigImportCommitter(catalog, split).commit(batch) { ++checks < 3 }

    assertTrue(result.isEmpty())
    assertEquals(listOf("office"), catalog.names())
    assertEquals("old-office", catalog.readConf("office"))
    assertEquals(original, split.read("office"))
  }

  @Test
  fun restoresAllPriorStateWhenSplitWriteFails() {
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "old-office"))
    val split = FailingSplitStore().apply {
      write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("old.app")))
    }
    val batch = ConfigImportBatch(
      listOf(
        ImportedTunnel("office", "new-office", StoredSplitTunnel()),
        ImportedTunnel("travel", "new-travel", StoredSplitTunnel()),
      ),
    )
    split.failName = "travel"

    val result = ConfigImportCommitter(catalog, split).commit(batch)

    assertTrue(result.isEmpty())
    assertEquals("old-office", catalog.readConf("office"))
    assertEquals(null, catalog.readConf("travel"))
    assertEquals(StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("old.app")), split.read("office"))
  }

  private class FailingSplitStore : SplitTunnelStore {
    private val values = mutableMapOf<String, StoredSplitTunnel>()
    var failName: String? = null

    override fun read(tunnelName: String): StoredSplitTunnel = values[tunnelName] ?: StoredSplitTunnel()

    override fun write(tunnelName: String, settings: StoredSplitTunnel) {
      if (tunnelName == failName) throw IllegalStateException("write failed")
      values[tunnelName] = settings
    }

    override fun delete(tunnelName: String) {
      values.remove(tunnelName)
    }
  }
}
