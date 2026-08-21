package com.wirepilot.app.control

import com.wirepilot.app.data.ConfigImportBatch

object ConfigImportPlanner {
  fun overwriteNames(batch: ConfigImportBatch, existingNames: Collection<String>): List<String> {
    return batch.tunnels.map { tunnel -> tunnel.name }
      .filter { name -> name in existingNames }
      .sorted()
  }
}
