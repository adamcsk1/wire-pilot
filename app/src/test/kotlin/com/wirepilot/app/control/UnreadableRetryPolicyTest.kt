package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnreadableRetryPolicyTest {
  private val unreadable = PolicyDecision.Skip(SkipReason.WIFI_SSID_UNREADABLE)

  @Test
  fun retriesAfterFirstDebounce() {
    assertTrue(UnreadableRetryPolicy.shouldRetry("debounce", unreadable))
    assertEquals("unreadable-retry-2", UnreadableRetryPolicy.nextTrigger("debounce"))
  }

  @Test
  fun retriesAfterProcessStart() {
    assertTrue(UnreadableRetryPolicy.shouldRetry(DebounceTriggers.PROCESS_START, unreadable))
  }

  @Test
  fun retriesUntilFifthAttempt() {
    assertTrue(UnreadableRetryPolicy.shouldRetry("debounce", unreadable))
    assertTrue(UnreadableRetryPolicy.shouldRetry("unreadable-retry-4", unreadable))
    assertFalse(UnreadableRetryPolicy.shouldRetry("unreadable-retry-5", unreadable))
  }

  @Test
  fun doesNotRetryManualApply() {
    assertFalse(UnreadableRetryPolicy.shouldRetry("apply-now", unreadable))
  }

  @Test
  fun doesNotRetrySuccessfulApply() {
    assertFalse(
      UnreadableRetryPolicy.shouldRetry("debounce", PolicyDecision.Apply(TunnelCommand.UP, "office")),
    )
  }

  @Test
  fun attemptNumbers() {
    assertEquals(1, UnreadableRetryPolicy.attemptNumber("debounce"))
    assertEquals(2, UnreadableRetryPolicy.attemptNumber("unreadable-retry-2"))
    assertEquals(5, UnreadableRetryPolicy.attemptNumber("unreadable-retry-5"))
    assertEquals(5, UnreadableRetryPolicy.MAX_ATTEMPTS)
  }
}
