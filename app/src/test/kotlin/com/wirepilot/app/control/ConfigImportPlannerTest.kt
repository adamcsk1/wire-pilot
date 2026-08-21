package com.wirepilot.app.control

import com.wirepilot.app.data.ConfigImportBatch
import com.wirepilot.app.data.ImportedTunnel
import com.wirepilot.app.data.StoredSplitTunnel
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigImportPlannerTest {
  @Test
  fun returnsSortedExistingNamesOnly() {
    val batch = ConfigImportBatch(
      listOf(imported("travel"), imported("office"), imported("home")),
    )

    assertEquals(
      listOf("home", "office"),
      ConfigImportPlanner.overwriteNames(batch, listOf("office", "home", "other")),
    )
  }

  private fun imported(name: String): ImportedTunnel {
    return ImportedTunnel(name, "[Interface]", StoredSplitTunnel())
  }
}
