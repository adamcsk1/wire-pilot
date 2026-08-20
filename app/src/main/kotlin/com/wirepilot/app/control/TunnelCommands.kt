package com.wirepilot.app.control

fun interface TunnelCommands {
  fun send(tunnelName: String, command: TunnelCommand)
}
