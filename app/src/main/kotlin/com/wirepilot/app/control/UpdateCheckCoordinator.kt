package com.wirepilot.app.control

import com.wirepilot.app.data.StoredUpdateCheck
import com.wirepilot.app.data.UpdateCheckStore

class UpdateCheckCoordinator(
  private val store: UpdateCheckStore,
  private val clock: () -> Long,
  private val installedVersionName: () -> String,
  private val fetchLatest: () -> GitHubReleaseFetch,
  private val scheduleAlarm: (atEpochMillis: Long) -> Unit,
  private val cancelAlarm: () -> Unit,
  private val showNotification: (tagName: String, htmlUrl: String) -> Boolean,
) {
  private val lock = Any()

  fun reschedule() {
    synchronized(lock) {
      rescheduleLocked()
    }
  }

  fun setNotifyEnabled(enabled: Boolean) {
    synchronized(lock) {
      store.write(store.read().copy(notifyEnabled = enabled))
      rescheduleLocked()
    }
  }

  fun checkNow(): UpdateCheckDecision {
    return runCheck(skipIfAlreadyNotified = false)
  }

  fun onPeriodicCheck() {
    val now = clock()
    val shouldFetch = synchronized(lock) {
      val stored = store.read()
      if (!stored.notifyEnabled) {
        cancelAlarm()
        false
      } else if (!UpdateCheckSchedule.isDue(stored.lastCheckEpochMillis, now)) {
        scheduleAlarm(UpdateCheckSchedule.nextAt(stored.lastCheckEpochMillis, now))
        false
      } else {
        store.write(stored.copy(lastCheckEpochMillis = now))
        scheduleAlarm(now + UpdateCheckSchedule.INTERVAL_MS)
        true
      }
    }
    if (shouldFetch) {
      runCheck(skipIfAlreadyNotified = true)
    }
  }

  private fun runCheck(skipIfAlreadyNotified: Boolean): UpdateCheckDecision {
    val fetch = fetchLatest()
    val decision = synchronized(lock) {
      val latest = store.read()
      val decided = UpdateCheckPolicy.decide(
        installedVersionName = installedVersionName(),
        fetch = fetch,
        lastNotifiedTag = latest.lastNotifiedTag,
        notifyEnabled = latest.notifyEnabled,
        skipIfAlreadyNotified = skipIfAlreadyNotified,
      )
      persistCheckTimeLocked(latest)
      rescheduleLocked()
      decided
    }
    if (decision is UpdateCheckDecision.Available && decision.notify) {
      if (showNotification(decision.tagName, decision.htmlUrl)) {
        synchronized(lock) {
          store.write(store.read().copy(lastNotifiedTag = decision.tagName))
        }
      }
    }
    return decision
  }

  private fun persistCheckTimeLocked(latest: StoredUpdateCheck) {
    store.write(latest.copy(lastCheckEpochMillis = clock()))
  }

  private fun rescheduleLocked() {
    val stored = store.read()
    if (!stored.notifyEnabled) {
      cancelAlarm()
      return
    }
    scheduleAlarm(UpdateCheckSchedule.nextAt(stored.lastCheckEpochMillis, clock()))
  }
}
