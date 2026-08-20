package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wirepilot.app.WirePilotApp

class PauseExpiredReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    (context.applicationContext as WirePilotApp).container.pauseExpiryCoordinator.onPauseExpired()
  }
}
