package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateCheckCodecTest {
  @Test
  fun roundTripsStoredState() {
    val value = StoredUpdateCheck(
      notifyEnabled = false,
      lastCheckEpochMillis = 42L,
      lastNotifiedTag = "1.0.1",
    )
    assertEquals(value, UpdateCheckCodec.decode(UpdateCheckCodec.encode(value)))
  }

  @Test
  fun defaultsMissingToNotifyEnabled() {
    assertEquals(StoredUpdateCheck(), UpdateCheckCodec.decode(null))
    assertEquals(StoredUpdateCheck(), UpdateCheckCodec.decode(""))
  }

  @Test
  fun sanitizesTabsInTag() {
    val encoded = UpdateCheckCodec.encode(StoredUpdateCheck(lastNotifiedTag = "1.0.1\tbad\n"))
    assertEquals("1.0.1 bad ", UpdateCheckCodec.decode(encoded).lastNotifiedTag)
  }

  @Test
  fun decodesPartialRecords() {
    assertEquals(
      StoredUpdateCheck(notifyEnabled = false, lastCheckEpochMillis = 0L, lastNotifiedTag = ""),
      UpdateCheckCodec.decode("0"),
    )
    assertEquals(
      StoredUpdateCheck(notifyEnabled = true, lastCheckEpochMillis = 0L, lastNotifiedTag = "1.0.1"),
      UpdateCheckCodec.decode("1\tnope\t1.0.1"),
    )
  }
}
