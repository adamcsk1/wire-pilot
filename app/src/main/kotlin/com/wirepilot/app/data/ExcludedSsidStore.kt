package com.wirepilot.app.data

interface ExcludedSsidStore {
  fun read(tunnelName: String): Set<String>
  fun write(tunnelName: String, ssids: Set<String>)
  fun delete(tunnelName: String)
  fun exists(tunnelName: String): Boolean
}

object EmptyExcludedSsidStore : ExcludedSsidStore {
  override fun read(tunnelName: String): Set<String> = emptySet()
  override fun write(tunnelName: String, ssids: Set<String>) = Unit
  override fun delete(tunnelName: String) = Unit
  override fun exists(tunnelName: String): Boolean = false
}
