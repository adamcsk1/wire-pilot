package com.wirepilot.app.platform

import android.os.Handler
import android.os.Looper
import com.wirepilot.app.control.DebounceAlarmPort
import com.wirepilot.app.control.DebounceArming
import com.wirepilot.app.control.DebouncePolicy
import com.wirepilot.app.control.DebounceTriggers
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.control.LogKind
import com.wirepilot.app.control.NoOpDiagnosticLog
import com.wirepilot.app.data.DebounceScheduleStore

class ReceiverDebouncer(
  private val alarms: AlarmScheduler,
  private val clock: () -> Long,
  private val scheduleStore: DebounceScheduleStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
  private val apply: (String) -> Unit = {},
) : DebounceAlarmPort {
  private val handler = Handler(Looper.getMainLooper())
  @Volatile
  var preferInProcess: Boolean = false
  private var pendingTrigger: String = DebounceTriggers.DEBOUNCE
  private var handlerPosted: Boolean = false
  private val runnable = Runnable {
    handlerPosted = false
    val trigger = pendingTrigger
    apply(trigger)
  }

  override fun scheduleDebouncedApply() {
    schedule(DebounceTriggers.DEBOUNCE, replace = false)
  }

  fun scheduleProcessStartApply() {
    schedule(DebounceTriggers.PROCESS_START, replace = false)
  }

  fun scheduleUnreadableRetry(trigger: String) {
    schedule(trigger, replace = true)
  }

  fun clearArmed() {
    scheduleStore.writeScheduledAtMillis(null)
    handlerPosted = false
    handler.removeCallbacks(runnable)
  }

  private fun schedule(trigger: String, replace: Boolean) {
    val nowMillis = clock()
    val armedUntil = scheduleStore.readScheduledAtMillis()
    if (!replace && !DebounceArming.shouldArm(nowMillis, armedUntil)) {
      log.record(LogKind.DEBOUNCE, "already armed until=$armedUntil")
      return
    }
    val scheduledAt = nowMillis + DebouncePolicy.WINDOW_MS
    scheduleStore.writeScheduledAtMillis(scheduledAt)
    pendingTrigger = trigger
    if (preferInProcess) {
      if (replace || !handlerPosted) {
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, DebouncePolicy.WINDOW_MS)
        handlerPosted = true
      }
      log.record(LogKind.DEBOUNCE, "armed in-process trigger=$trigger until=$scheduledAt")
    } else {
      alarms.scheduleDebounce(trigger, scheduledAt)
      log.record(LogKind.DEBOUNCE, "armed alarm trigger=$trigger until=$scheduledAt")
    }
  }
}
