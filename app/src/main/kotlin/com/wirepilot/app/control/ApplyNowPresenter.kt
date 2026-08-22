package com.wirepilot.app.control

enum class ApplyNowAction {
  APPLY,
  UNAVAILABLE,
}

data class ApplyNowView(
  val action: ApplyNowAction,
  val enabled: Boolean,
  val visible: Boolean,
  val skipReason: SkipReason?,
)

object ApplyNowPresenter {
  fun present(
    decision: PolicyDecision,
    controlSelection: ControlSelection,
  ): ApplyNowView {
    if (controlSelection != ControlSelection.ON) {
      return ApplyNowView(ApplyNowAction.UNAVAILABLE, enabled = false, visible = false, skipReason = null)
    }
    return when (decision) {
      is PolicyDecision.Apply -> ApplyNowView(ApplyNowAction.APPLY, enabled = true, visible = true, skipReason = null)
      is PolicyDecision.Skip -> ApplyNowView(
        ApplyNowAction.UNAVAILABLE,
        enabled = false,
        visible = true,
        skipReason = decision.reason,
      )
    }
  }
}
