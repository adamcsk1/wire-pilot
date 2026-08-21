package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.LogFormatter
import com.wirepilot.app.data.LogKind

class NetworkChangeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val application = context.applicationContext as WirePilotApp
    val snapshot = application.container.ssidReader.snapshot()
    application.container.logger.record(
      LogKind.NETWORK_CHANGE,
      LogFormatter.networkChangeDetail(snapshot) + " source=broadcast",
    )
    application.container.networkChangeCoordinator.onNetworkChanged()
  }
}
