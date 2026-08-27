package com.wirepilot.app.control

object UpdateNotifyGate {
  const val IMPORTANCE_NONE = 0

  fun canPost(notificationsEnabled: Boolean, channelImportance: Int): Boolean {
    return notificationsEnabled && channelImportance > IMPORTANCE_NONE
  }
}
