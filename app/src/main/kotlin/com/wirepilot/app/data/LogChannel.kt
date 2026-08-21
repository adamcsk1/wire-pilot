package com.wirepilot.app.data

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
