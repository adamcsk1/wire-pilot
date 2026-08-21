package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class TunnelIdleActionsTest {
  @Test
  fun idleTunnelRunsImmediatelyAndRemovesListener() {
    var listener: (() -> Unit)? = null
    var removed = 0
    var actions = 0
    val idleActions = TunnelIdleActions(
      isIdle = { true },
      addSettledListener = { added -> listener = added },
      removeSettledListener = { removed += 1 },
      dispatch = { dispatched -> dispatched() },
    )

    idleActions.run { actions += 1 }
    listener?.invoke()

    assertEquals(1, actions)
    assertEquals(1, removed)
  }

  @Test
  fun pendingTunnelWaitsForSettledSignal() {
    var idle = false
    var listener: (() -> Unit)? = null
    var actions = 0
    val idleActions = TunnelIdleActions(
      isIdle = { idle },
      addSettledListener = { added -> listener = added },
      removeSettledListener = {},
      dispatch = { dispatched -> dispatched() },
    )

    idleActions.run { actions += 1 }
    assertEquals(0, actions)

    idle = true
    listener?.invoke()
    listener?.invoke()
    assertEquals(1, actions)
  }

  @Test
  fun cancellationRemovesListenerAndPreventsAction() {
    var listener: (() -> Unit)? = null
    var removed = 0
    var actions = 0
    val idleActions = TunnelIdleActions(
      isIdle = { true },
      addSettledListener = { added -> listener = added },
      removeSettledListener = { removed += 1 },
      dispatch = {},
    )

    val cancel = idleActions.run { actions += 1 }
    cancel()
    listener?.invoke()

    assertEquals(0, actions)
    assertEquals(1, removed)
  }
}
