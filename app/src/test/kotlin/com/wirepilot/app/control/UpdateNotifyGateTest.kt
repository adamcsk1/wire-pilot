package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateNotifyGateTest {
  @Test
  fun postsWhenEnabledAndChannelAudible() {
    assertTrue(UpdateNotifyGate.canPost(notificationsEnabled = true, channelImportance = 3))
    assertTrue(UpdateNotifyGate.canPost(notificationsEnabled = true, channelImportance = 1))
  }

  @Test
  fun blocksWhenNotificationsDisabled() {
    assertFalse(UpdateNotifyGate.canPost(notificationsEnabled = false, channelImportance = 3))
  }

  @Test
  fun blocksWhenChannelImportanceNone() {
    assertFalse(
      UpdateNotifyGate.canPost(
        notificationsEnabled = true,
        channelImportance = UpdateNotifyGate.IMPORTANCE_NONE,
      ),
    )
  }
}
