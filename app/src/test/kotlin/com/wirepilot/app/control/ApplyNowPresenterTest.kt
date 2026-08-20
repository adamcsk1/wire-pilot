package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyNowPresenterTest {
  @Test
  fun applyWhenPolicyCanRun() {
    val view = ApplyNowPresenter.present(PolicyDecision.Apply(TunnelCommand.UP))
    assertEquals(ApplyNowAction.APPLY, view.action)
    assertTrue(view.enabled)
    assertNull(view.skipReason)
  }

  @Test
  fun applyWhenPolicyWouldDisconnect() {
    val view = ApplyNowPresenter.present(PolicyDecision.Apply(TunnelCommand.DOWN))
    assertEquals(ApplyNowAction.APPLY, view.action)
    assertTrue(view.enabled)
  }

  @Test
  fun unavailableWhenSkipped() {
    val view = ApplyNowPresenter.present(PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME))
    assertEquals(ApplyNowAction.UNAVAILABLE, view.action)
    assertFalse(view.enabled)
    assertEquals(SkipReason.BLANK_TUNNEL_NAME, view.skipReason)
  }
}
