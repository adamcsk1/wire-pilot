package com.wirepilot.app.support

import com.wirepilot.app.control.WatchingServicePort

class RecordingWatching : WatchingServicePort {
  val values = mutableListOf<Boolean>()

  override fun sync(watching: Boolean) {
    values += watching
  }
}
