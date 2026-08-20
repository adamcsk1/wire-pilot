package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.DebounceTriggers

class ApplyDebounceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val trigger = intent?.getStringExtra(DebounceTriggers.EXTRA_TRIGGER) ?: DebounceTriggers.DEBOUNCE
    val application = context.applicationContext as WirePilotApp
    application.container.runDebouncedApply(trigger)
  }
}
