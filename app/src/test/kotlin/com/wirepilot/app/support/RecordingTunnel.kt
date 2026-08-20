package com.wirepilot.app.support

import com.wirepilot.app.control.TunnelCommand
import com.wirepilot.app.control.TunnelCommands

class RecordingTunnel : TunnelCommands {
  val commands = mutableListOf<Pair<String, TunnelCommand>>()

  override fun send(tunnelName: String, command: TunnelCommand) {
    commands += tunnelName to command
  }
}
