package com.wirepilot.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wirepilot.app.control.DebounceTriggers
import com.wirepilot.app.control.PauseAlarmPort
import com.wirepilot.app.receiver.ApplyDebounceReceiver
import com.wirepilot.app.receiver.PauseExpiredReceiver

class AlarmScheduler(
  private val context: Context,
) {
  private val alarmManager = context.getSystemService(AlarmManager::class.java)

  fun pausePort(): PauseAlarmPort = object : PauseAlarmPort {
    override fun schedule(atEpochMillis: Long) {
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pauseIntent())
    }

    override fun cancel() {
      alarmManager.cancel(pauseIntent())
    }
  }

  fun scheduleDebounce(trigger: String, atEpochMillis: Long) {
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, debounceIntent(trigger))
  }

  fun cancelDebounce() {
    alarmManager.cancel(debounceIntent(DebounceTriggers.DEBOUNCE))
  }

  private fun pauseIntent(): PendingIntent {
    return PendingIntent.getBroadcast(
      context,
      REQUEST_PAUSE,
      Intent(context, PauseExpiredReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun debounceIntent(trigger: String): PendingIntent {
    val intent = Intent(context, ApplyDebounceReceiver::class.java).apply {
      putExtra(DebounceTriggers.EXTRA_TRIGGER, trigger)
    }
    return PendingIntent.getBroadcast(
      context,
      REQUEST_DEBOUNCE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
  }

  companion object {
    private const val REQUEST_PAUSE = 31
    private const val REQUEST_DEBOUNCE = 32
  }
}
