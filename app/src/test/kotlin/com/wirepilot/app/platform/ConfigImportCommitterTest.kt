package com.wirepilot.app.platform

import com.wirepilot.app.data.ConfigImportBatch
import com.wirepilot.app.data.ImportedTunnel
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.support.InMemoryTunnelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigImportCommitterTest {
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
