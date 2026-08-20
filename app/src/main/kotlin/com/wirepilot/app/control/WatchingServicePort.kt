package com.wirepilot.app.control

fun interface WatchingServicePort {
  fun sync(watching: Boolean)
}

object NoOpWatchingService : WatchingServicePort {
  override fun sync(watching: Boolean) = Unit
}

object WatchingPolicy {
  fun shouldWatch(status: StatusPresentation): Boolean {
    return status is StatusPresentation.Watching
  }
}

enum class WatchingOutcome {
  START,
  STOP,
  NO_PERMISSION,
}

object WatchingSyncPolicy {
  fun outcome(wantWatch: Boolean, notificationsGranted: Boolean): WatchingOutcome {
    if (!wantWatch) {
      return WatchingOutcome.STOP
    }
    if (!notificationsGranted) {
      return WatchingOutcome.NO_PERMISSION
    }
    return WatchingOutcome.START
  }
}
