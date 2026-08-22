package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyNowPresenterTest {
  @Test
  fun applyWhenPolicyCanRun() {
    val view = ApplyNowPresenter.present(
      PolicyDecision.Apply(TunnelCommand.UP, "office"),
      ControlSelection.ON,
    )
    assertEquals(ApplyNowAction.APPLY, view.action)
    assertTrue(view.enabled)
    assertTrue(view.visible)
    assertNull(view.skipReason)
  }

  @Test
  fun applyWhenPolicyWouldDisconnect() {
    val view = ApplyNowPresenter.present(
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      ControlSelection.ON,
    )
    assertEquals(ApplyNowAction.APPLY, view.action)
    assertTrue(view.enabled)
    assertTrue(view.visible)
  }

  @Test
  fun unavailableWhenSkipped() {
    val view = ApplyNowPresenter.present(
      PolicyDecision.Skip(SkipReason.BLANK_TUNNEL_NAME),
      ControlSelection.ON,
    )
    assertEquals(ApplyNowAction.UNAVAILABLE, view.action)
    assertFalse(view.enabled)
    assertTrue(view.visible)
    assertEquals(SkipReason.BLANK_TUNNEL_NAME, view.skipReason)
  }

  @Test
  fun hiddenWhenControlOff() {
    val view = ApplyNowPresenter.present(
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      ControlSelection.OFF,
    )
    assertEquals(ApplyNowAction.UNAVAILABLE, view.action)
    assertFalse(view.enabled)
    assertFalse(view.visible)
    assertNull(view.skipReason)
  }

  @Test
  fun hiddenWhenPaused() {
    val view = ApplyNowPresenter.present(
      PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
      ControlSelection.PAUSE,
    )
    assertEquals(ApplyNowAction.UNAVAILABLE, view.action)
    assertFalse(view.enabled)
    assertFalse(view.visible)
    assertNull(view.skipReason)
  }
}
