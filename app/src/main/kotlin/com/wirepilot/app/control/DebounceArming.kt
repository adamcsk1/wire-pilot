package com.wirepilot.app.control

object DebounceArming {
  fun shouldArm(nowMillis: Long, scheduledAtMillis: Long?): Boolean {
    return scheduledAtMillis == null || nowMillis >= scheduledAtMillis
  }
}
