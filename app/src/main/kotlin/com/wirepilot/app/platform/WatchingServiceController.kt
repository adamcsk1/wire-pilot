package com.wirepilot.app.platform

import android.content.Context
import android.util.Log
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.control.WatchingOutcome
import com.wirepilot.app.control.WatchingServicePort
import com.wirepilot.app.control.WatchingSyncPolicy

class WatchingServiceController(
  private val context: Context,
  private val log: DiagnosticLog,
  private val notificationsGranted: () -> Boolean,
) : WatchingServicePort {
  private var lastDetail: String? = null

  override fun sync(watching: Boolean) {
    when (WatchingSyncPolicy.outcome(watching, notificationsGranted())) {
      WatchingOutcome.STOP -> {
        WatchingService.stop(context)
        lastDetail = null
      }
      WatchingOutcome.NO_PERMISSION -> {
        WatchingService.stop(context)
        logOnce("no-permission")
      }
      WatchingOutcome.START -> startWatching()
    }
  }

  private fun startWatching() {
    try {
      WatchingService.start(context)
      logOnce("started")
    } catch (error: RuntimeException) {
      Log.e(TAG, "watching start failed: ${error.message}", error)
      logOnce("failed ${error.javaClass.simpleName}")
    }
  }

  private fun logOnce(detail: String) {
    if (detail == lastDetail) {
      return
    }
    lastDetail = detail
    log.record(LogKind.WATCHING, detail)
  }

  companion object {
    private const val TAG = "WirePilot"
  }
}
