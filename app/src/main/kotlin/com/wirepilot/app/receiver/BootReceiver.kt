package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.LogKind

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
      return
    }
    val application = context.applicationContext as WirePilotApp
    application.container.logger.record(LogKind.BOOT, action)
    application.container.bootCoordinator.onBootOrUpdate()
  }
}
