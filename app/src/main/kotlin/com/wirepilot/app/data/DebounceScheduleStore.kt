package com.wirepilot.app.data

interface DebounceScheduleStore {
  fun readScheduledAtMillis(): Long?
  fun writeScheduledAtMillis(scheduledAtMillis: Long?)
}
