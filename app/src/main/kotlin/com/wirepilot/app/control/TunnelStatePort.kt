package com.wirepilot.app.control

fun interface TunnelStatePort {
  fun isUp(tunnelName: String): Boolean
}

object NoOpTunnelState : TunnelStatePort {
  override fun isUp(tunnelName: String): Boolean = false
}
