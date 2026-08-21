package com.wirepilot.app.control

data class InventoryLink(
  val wifi: Boolean,
  val cellular: Boolean,
  val rawSsid: String?,
)

object InventoryMapper {
  fun toSnapshot(links: List<InventoryLink>, ssidSource: String = "transport"): NetworkSnapshot {
    return NetworkSnapshotFactory.fromRawWifiSsids(
      rawSsids = links.filter { it.wifi }.map { it.rawSsid },
      hasAnyWifi = links.any { it.wifi },
      hasCellular = links.any { it.cellular },
      ssidSource = ssidSource,
    )
  }
}
