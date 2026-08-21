package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SsidNormalizerTest {
  @Test
  fun normalizeReturnsNullForNull() {
    assertNull(SsidNormalizer.normalize(null))
  }

  @Test
  fun normalizeReturnsNullForBlank() {
    assertNull(SsidNormalizer.normalize("   "))
  }

  @Test
  fun normalizeStripsQuotes() {
    assertEquals("Home", SsidNormalizer.normalize("\"Home\""))
  }

  @Test
  fun normalizeTrimsWhitespace() {
    assertEquals("Home", SsidNormalizer.normalize("  Home  "))
  }

  @Test
  fun normalizeReturnsNullForUnknownSsid() {
    assertNull(SsidNormalizer.normalize("<unknown ssid>"))
  }

  @Test
  fun normalizeReturnsNullForUnknownSsidCaseInsensitive() {
    assertNull(SsidNormalizer.normalize("<UNKNOWN SSID>"))
  }

  @Test
  fun normalizeReturnsNullForUnknownSsidWithoutBrackets() {
    assertNull(SsidNormalizer.normalize("unknown ssid"))
  }

  @Test
  fun normalizeReturnsNullForQuotedUnknownSsid() {
    assertNull(SsidNormalizer.normalize("\"<unknown ssid>\""))
  }

  @Test
  fun normalizeReturnsNullForEmptyQuotes() {
    assertNull(SsidNormalizer.normalize("\"\""))
  }

  @Test
  fun normalizeKeepsSsidWithInternalQuotes() {
    assertEquals("He said \"hi\"", SsidNormalizer.normalize("He said \"hi\""))
  }
}
