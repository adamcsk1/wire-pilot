package com.wirepilot.app.data

interface TunnelCatalog {
  fun names(): List<String>
  fun readConf(name: String): String?
  fun writeConf(name: String, conf: String)
  fun delete(name: String)
}

object EmptyTunnelCatalog : TunnelCatalog {
  override fun names(): List<String> = emptyList()
  override fun readConf(name: String): String? = null
  override fun writeConf(name: String, conf: String) = Unit
  override fun delete(name: String) = Unit
}
