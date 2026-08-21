package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LastKnownRememberPolicyTest {
  @Test
  fun freshTransportObservationIsRememberedOnce() {
    val snapshot = NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), ssidSource = "transport")

    assertTrue(LastKnownRememberPolicy.shouldRemember(snapshot, readableRevision = 2L, rememberedRevision = 1L))
    assertFalse(LastKnownRememberPolicy.shouldRemember(snapshot, readableRevision = 2L, rememberedRevision = 2L))
    assertFalse(LastKnownRememberPolicy.shouldRemember(snapshot, readableRevision = 1L, rememberedRevision = 2L))
  }

  @Test
  fun unreadableOrUnversionedTransportIsNotRemembered() {
    assertFalse(
      LastKnownRememberPolicy.shouldRemember(
        NetworkSnapshot(NetworkKind.WIFI_SETTLING),
        readableRevision = 1L,
        rememberedRevision = 0L,
      ),
    )
    assertFalse(
      LastKnownRememberPolicy.shouldRemember(
        NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), ssidSource = "transport"),
        readableRevision = 0L,
        rememberedRevision = 0L,
      ),
    )
  }
}
