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
    assertEquals(13, redacted.length)
    assertEquals(false, redacted.matches(Regex("h[0-9a-f]{4}")))
  }

  @Test
  fun nullBecomesLiteralNull() {
    assertEquals("null", SsidRedactor.redactNullable(null))
    assertEquals(SsidRedactor.redact("Home"), SsidRedactor.redactNullable("Home"))
  }

  @Test
  fun differentKeysHashDifferently() {
    val first = SsidRedactor.redact("Home", ByteArray(32) { 1 })
    val second = SsidRedactor.redact("Home", ByteArray(32) { 2 })
    assertNotEquals(first, second)
    assertEquals(SsidRedactor.redact("Home", ByteArray(32) { 1 }), first)
  }

  @Test
  fun installedKeyIsUsed() {
    val previous = SsidRedactor.redact("Home")
    SsidRedactor.installKey(ByteArray(32) { 3 })
    try {
      val installed = SsidRedactor.redact("Home")
      assertEquals(SsidRedactor.redact("Home", ByteArray(32) { 3 }), installed)
      assertNotEquals(previous, installed)
    } finally {
      SsidRedactor.installKey(ByteArray(0))
    }
  }
}
