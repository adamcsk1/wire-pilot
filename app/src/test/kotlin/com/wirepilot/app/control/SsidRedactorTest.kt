package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SsidRedactorTest {
  private val zeroKey = ByteArray(0)

  @Test
  fun hashesSameNameTheSame() {
    assertEquals(SsidRedactor.redact("Home", zeroKey), SsidRedactor.redact("Home", zeroKey))
  }

  @Test
  fun differentNamesHashDifferently() {
    assertNotEquals(SsidRedactor.redact("Home", zeroKey), SsidRedactor.redact("Cafe", zeroKey))
  }

  @Test
  fun doesNotContainOriginalName() {
    val redacted = SsidRedactor.redact("HomeWifi", zeroKey)
    assertEquals(false, redacted.contains("HomeWifi"))
    assertEquals(true, redacted.startsWith("h"))
    assertEquals(13, redacted.length)
    assertEquals(false, redacted.matches(Regex("h[0-9a-f]{4}")))
  }

  @Test
  fun nullBecomesLiteralNull() {
    assertEquals("null", SsidRedactor.redactNullable(null, zeroKey))
    assertEquals(SsidRedactor.redact("Home", zeroKey), SsidRedactor.redactNullable("Home", zeroKey))
  }

  @Test
  fun differentKeysHashDifferently() {
    val first = SsidRedactor.redact("Home", ByteArray(32) { 1 })
    val second = SsidRedactor.redact("Home", ByteArray(32) { 2 })
    assertNotEquals(first, second)
    assertEquals(SsidRedactor.redact("Home", ByteArray(32) { 1 }), first)
  }

  @Test
  fun undersizedKeyUsesZeroSecret() {
    assertEquals(SsidRedactor.redact("Home", ByteArray(0)), SsidRedactor.redact("Home", ByteArray(31)))
  }
}
