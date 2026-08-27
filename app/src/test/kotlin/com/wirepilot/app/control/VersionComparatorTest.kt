package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionComparatorTest {
  @Test
  fun parsesThreePartTags() {
    assertEquals(VersionComparator.SemanticVersion(1, 0, 0), VersionComparator.parse("1.0.0"))
    assertEquals(VersionComparator.SemanticVersion(1, 0, 1), VersionComparator.parse("1.0.1"))
    assertEquals(VersionComparator.SemanticVersion(2, 0, 0), VersionComparator.parse("2.0.0"))
  }

  @Test
  fun stripsVPrefixAndPreReleaseSuffix() {
    assertEquals(VersionComparator.SemanticVersion(1, 0, 1), VersionComparator.parse("v1.0.1"))
    assertEquals(VersionComparator.SemanticVersion(1, 0, 1), VersionComparator.parse("V1.0.1"))
    assertEquals(VersionComparator.SemanticVersion(1, 2, 3), VersionComparator.parse("1.2.3-rc.1"))
    assertEquals(VersionComparator.SemanticVersion(1, 2, 3), VersionComparator.parse("1.2.3+build"))
  }

  @Test
  fun treatsTwoPartAsPatchZero() {
    assertEquals(VersionComparator.SemanticVersion(1, 0, 0), VersionComparator.parse("1.0"))
  }

  @Test
  fun rejectsGarbage() {
    assertNull(VersionComparator.parse(""))
    assertNull(VersionComparator.parse("   "))
    assertNull(VersionComparator.parse("latest"))
    assertNull(VersionComparator.parse("1"))
    assertNull(VersionComparator.parse("1.x.0"))
    assertNull(VersionComparator.parse("-1.0.0"))
    assertNull(VersionComparator.parse("1.-2.0"))
    assertNull(VersionComparator.parse("1.0.-3"))
    assertNull(VersionComparator.parse("1.0.0.1"))
  }

  @Test
  fun comparesSemver() {
    assertEquals(1, VersionComparator.compare("1.0.1", "1.0.0"))
    assertEquals(-1, VersionComparator.compare("1.0.0", "1.0.1"))
    assertEquals(0, VersionComparator.compare("v1.0.1", "1.0.1"))
    assertEquals(1, VersionComparator.compare("2.0.0", "1.9.9"))
    assertNull(VersionComparator.compare("nope", "1.0.0"))
  }

  @Test
  fun isNewerTreatsUnparseableInstalledAsOlder() {
    assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0") == true)
    assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1") == true)
    assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0") == true)
    assertTrue(VersionComparator.isNewer("1.0.1", "") == true)
    assertNull(VersionComparator.isNewer("nope", "1.0.0"))
    assertNotNull(VersionComparator.parse("1.0.0"))
  }
}
