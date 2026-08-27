package com.wirepilot.app.control

sealed class UpdateCheckDecision {
  data object UpToDate : UpdateCheckDecision()
  data object NoRelease : UpdateCheckDecision()
  data object Failed : UpdateCheckDecision()
  data object AlreadyNotified : UpdateCheckDecision()
  data class Available(
    val tagName: String,
    val htmlUrl: String,
    val notify: Boolean,
  ) : UpdateCheckDecision()
}
