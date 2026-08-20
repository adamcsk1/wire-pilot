package com.wirepilot.app.control

enum class LogChannel {
  POLICY,
  VPN,
}

object LogChannels {
  fun of(kind: LogKind): LogChannel {
    return when (kind) {
      LogKind.TUNNEL, LogKind.TUNNEL_ERROR -> LogChannel.VPN
      else -> LogChannel.POLICY
    }
  }
}
