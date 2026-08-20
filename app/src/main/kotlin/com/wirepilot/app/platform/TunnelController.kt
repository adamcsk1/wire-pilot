package com.wirepilot.app.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.wirepilot.app.control.TunnelCommand
import com.wirepilot.app.control.TunnelCommands
import com.wirepilot.app.control.TunnelIntentFactory

class TunnelController(
  private val context: Context,
) : TunnelCommands {
  override fun send(tunnelName: String, command: TunnelCommand) {
    val spec = TunnelIntentFactory.create(tunnelName, command)
    val intent = Intent(spec.action).apply {
      component = ComponentName(spec.packageName, spec.receiverClassName)
      spec.extras.forEach { entry -> putExtra(entry.key, entry.value) }
      if (spec.includeStoppedPackages) {
        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
      }
    }
    context.sendBroadcast(intent)
  }
}
