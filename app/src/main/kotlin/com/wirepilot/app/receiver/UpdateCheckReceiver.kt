package com.wirepilot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.BoundedCompletion

class UpdateCheckReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val application = context.applicationContext as WirePilotApp
    val pendingResult = goAsync()
    val completion = BoundedCompletion(
      finish = { pendingResult.finish() },
      scheduleTimeout = { action -> Handler(Looper.getMainLooper()).postDelayed(action, MAX_RECEIVER_MS) },
    )
    completion.arm()
    val cancel = application.container.updateCheckRunner.runPeriodic {
      completion.complete()
    }
    completion.setCancellation(cancel)
  }

  companion object {
    private const val MAX_RECEIVER_MS = 8_000L
  }
}
