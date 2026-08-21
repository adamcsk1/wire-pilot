package com.wirepilot.app.control

fun interface TunnelCommands {
  fun send(tunnelName: String, command: TunnelCommand)

  fun send(commands: List<Pair<String, TunnelCommand>>) {
    commands.forEach { (tunnelName, command) -> send(tunnelName, command) }
  }
}
