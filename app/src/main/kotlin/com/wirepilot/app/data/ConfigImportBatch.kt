package com.wirepilot.app.data

data class ImportedTunnel(
  val name: String,
  val conf: String,
  val splitTunnel: StoredSplitTunnel,
)

data class ConfigImportBatch(
  val tunnels: List<ImportedTunnel>,
)
