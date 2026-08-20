package com.wirepilot.app.control

enum class ControlSelection {
  ON,
  PAUSE,
  OFF,
}

object ControlSelectionPresenter {
  fun present(status: StatusPresentation): ControlSelection {
    return when (status) {
      StatusPresentation.Watching -> ControlSelection.ON
      is StatusPresentation.Paused -> ControlSelection.PAUSE
      StatusPresentation.Disabled -> ControlSelection.OFF
    }
  }
}
