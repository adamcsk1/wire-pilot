package com.wirepilot.app.platform

import com.wirepilot.app.control.DebounceAlarmPort
import com.wirepilot.app.control.DebounceArming
import com.wirepilot.app.control.DebouncePolicy
import com.wirepilot.app.control.DebounceTriggers
import com.wirepilot.app.control.DiagnosticLog
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.control.NoOpDiagnosticLog
import com.wirepilot.app.data.DebounceScheduleStore

class ReceiverDebouncer(
  private val alarms: AlarmScheduler,
  private val clock: () -> Long,
  private val scheduleStore: DebounceScheduleStore,
  private val log: DiagnosticLog = NoOpDiagnosticLog,
) : DebounceAlarmPort {
  override fun scheduleDebouncedApply() {
    schedule(DebounceTriggers.DEBOUNCE, replace = false)
  }

  fun scheduleProcessStartApply() {
    schedule(DebounceTriggers.PROCESS_START, replace = false)
  }

  fun clearArmed() {
    scheduleStore.writeScheduledAtMillis(null)
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
    alarms.scheduleDebounce(trigger, scheduledAt)
    log.record(LogKind.DEBOUNCE, "armed alarm trigger=$trigger until=$scheduledAt")
  }
}
