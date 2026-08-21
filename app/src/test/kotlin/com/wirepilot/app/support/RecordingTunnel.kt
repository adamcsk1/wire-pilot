package com.wirepilot.app.support

import com.wirepilot.app.control.TunnelCommand
import com.wirepilot.app.control.TunnelCommands
import com.wirepilot.app.control.TunnelStatePort

class RecordingTunnel : TunnelCommands, TunnelStatePort {
  val commands = mutableListOf<Pair<String, TunnelCommand>>()
  private val upNames = mutableSetOf<String>()

  override fun send(tunnelName: String, command: TunnelCommand) {
    commands += tunnelName to command
    if (command == TunnelCommand.UP) {
      upNames += tunnelName
    } else {
      upNames -= tunnelName
    }
  }

  override fun isUp(tunnelName: String): Boolean = tunnelName in upNames
}
