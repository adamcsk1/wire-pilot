package com.wirepilot.app.control

enum class ApplyNowAction {
  APPLY,
  UNAVAILABLE,
}

data class ApplyNowView(
  val action: ApplyNowAction,
  val enabled: Boolean,
  val skipReason: SkipReason?,
)

object ApplyNowPresenter {
  fun present(decision: PolicyDecision): ApplyNowView {
    return when (decision) {
      is PolicyDecision.Apply -> ApplyNowView(ApplyNowAction.APPLY, true, null)
      is PolicyDecision.Skip -> ApplyNowView(ApplyNowAction.UNAVAILABLE, false, decision.reason)
    }
  }
}
