package com.wirepilot.app.control

import com.wirepilot.app.data.StoredControl

sealed class StatusPresentation {
  data object Watching : StatusPresentation()
  data object Disabled : StatusPresentation()
  data class Paused(val remainingMillis: Long) : StatusPresentation()
}

object StatusPresenter {
  fun present(control: StoredControl, nowMillis: Long): StatusPresentation {
    val resolved = ControlModeResolver.resolve(control, nowMillis)
    val pauseUntil = resolved.pausedUntilEpochMillis
    return when {
      resolved.enabled -> StatusPresentation.Watching
      pauseUntil != null -> StatusPresentation.Paused((pauseUntil - nowMillis).coerceAtLeast(0L))
      else -> StatusPresentation.Disabled
    }
  }
}
