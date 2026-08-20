package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SsidRedactorTest {
  @Test
  fun hashesSameNameTheSame() {
    assertEquals(SsidRedactor.redact("Home"), SsidRedactor.redact("Home"))
  }

  @Test
  fun differentNamesHashDifferently() {
    assertNotEquals(SsidRedactor.redact("Home"), SsidRedactor.redact("Cafe"))
  }

  @Test
  fun doesNotContainOriginalName() {
    val redacted = SsidRedactor.redact("HomeWifi")
    assertEquals(false, redacted.contains("HomeWifi"))
    assertEquals(true, redacted.startsWith("h"))
  }

  @Test
  fun nullBecomesLiteralNull() {
    assertEquals("null", SsidRedactor.redactNullable(null))
    assertEquals(SsidRedactor.redact("Home"), SsidRedactor.redactNullable("Home"))
  }
}
