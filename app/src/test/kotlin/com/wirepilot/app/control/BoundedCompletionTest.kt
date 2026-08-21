package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedCompletionTest {
  @Test
  fun workCompletionFinishesAndCancelsExactlyOnce() {
    var timeout: (() -> Unit)? = null
    var finishes = 0
    var cancellations = 0
    val completion = BoundedCompletion(
      finish = { finishes += 1 },
      scheduleTimeout = { action -> timeout = action },
    )
    completion.setCancellation { cancellations += 1 }
    completion.arm()

    completion.complete()
    timeout?.invoke()

    assertEquals(1, finishes)
    assertEquals(1, cancellations)
  }

  @Test
  fun timeoutBeforeCancellationStillCancelsLateWork() {
    var timeout: (() -> Unit)? = null
    var finishes = 0
    var cancellations = 0
    val completion = BoundedCompletion(
      finish = { finishes += 1 },
      scheduleTimeout = { action -> timeout = action },
    )
    completion.arm()

    timeout?.invoke()
    completion.setCancellation { cancellations += 1 }
    completion.complete()

    assertEquals(1, finishes)
    assertEquals(1, cancellations)
  }
}
