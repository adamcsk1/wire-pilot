package com.wirepilot.app.control

data class TunnelTraffic(
  val rxBytes: Long,
  val txBytes: Long,
)

fun interface TunnelStatsPort {
  fun traffic(tunnelName: String): TunnelTraffic?
}

object NoOpTunnelStats : TunnelStatsPort {
  override fun traffic(tunnelName: String): TunnelTraffic? = null
}
