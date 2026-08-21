package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatterTest {
  @Test
  fun formatsBytes() {
    assertEquals("0 B", ByteFormatter.format(0L))
    assertEquals("512 B", ByteFormatter.format(512L))
    assertEquals("1023 B", ByteFormatter.format(1023L))
  }

  @Test
  fun formatsLargerUnits() {
    assertEquals("1.00 KiB", ByteFormatter.format(1024L))
    assertEquals("10.0 KiB", ByteFormatter.format(10L * 1024L))
    assertEquals("100 KiB", ByteFormatter.format(100L * 1024L))
    assertEquals("1.50 MiB", ByteFormatter.format((1536L * 1024L)))
    assertEquals("1.00 GiB", ByteFormatter.format(1024L * 1024L * 1024L))
    assertEquals("1.00 TiB", ByteFormatter.format(1024L * 1024L * 1024L * 1024L))
  }

  @Test
  fun clampsNegativeToZero() {
    assertEquals("0 B", ByteFormatter.format(-8L))
  }
}
