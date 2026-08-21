package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.BoundedCompletion
import com.wirepilot.app.control.DebounceTriggers
import android.os.Handler
import android.os.Looper

class ApplyDebounceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val trigger = intent?.getStringExtra(DebounceTriggers.EXTRA_TRIGGER) ?: DebounceTriggers.DEBOUNCE
    val application = context.applicationContext as WirePilotApp
    val pendingResult = goAsync()
    val completion = BoundedCompletion(
      finish = { pendingResult.finish() },
      scheduleTimeout = { action -> Handler(Looper.getMainLooper()).postDelayed(action, MAX_RECEIVER_MS) },
    )
    completion.arm()
    val cancel = application.container.runDebouncedApply(trigger) { completion.complete() }
    completion.setCancellation(cancel)
  }

  companion object {
    private const val MAX_RECEIVER_MS = 8_000L
  }
}
