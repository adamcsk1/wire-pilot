package com.wirepilot.app.control

import com.wirepilot.app.data.GitHubRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckPolicyTest {
  @Test
  fun failedFetchIsFailed() {
    assertEquals(
      UpdateCheckDecision.Failed,
      decide(fetch = GitHubReleaseFetch.Failed),
    )
  }

  @Test
  fun missingReleaseIsNoRelease() {
    assertEquals(
      UpdateCheckDecision.NoRelease,
      decide(fetch = GitHubReleaseFetch.NotFound),
    )
  }

  @Test
  fun olderOrEqualRemoteIsUpToDate() {
    assertEquals(UpdateCheckDecision.UpToDate, decide(remote = "1.0.0"))
    assertEquals(UpdateCheckDecision.UpToDate, decide(remote = "0.9.0"))
  }

  @Test
  fun newerRemoteNotifiesWhenEnabled() {
    val decision = decide(remote = "1.0.1")
    assertTrue(decision is UpdateCheckDecision.Available)
    decision as UpdateCheckDecision.Available
    assertEquals("1.0.1", decision.tagName)
    assertEquals("https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1", decision.htmlUrl)
    assertTrue(decision.notify)
  }

  @Test
  fun newerRemoteDoesNotNotifyWhenDisabled() {
    val decision = decide(remote = "1.0.1", notifyEnabled = false)
    assertTrue(decision is UpdateCheckDecision.Available)
    assertFalse((decision as UpdateCheckDecision.Available).notify)
  }

  @Test
  fun alreadyNotifiedSkipsOnPeriodic() {
    assertEquals(
      UpdateCheckDecision.AlreadyNotified,
      decide(remote = "v1.0.1", lastNotifiedTag = "1.0.1", skipIfAlreadyNotified = true),
    )
  }

  @Test
  fun alreadyNotifiedStillShowsOnManualWithoutNotify() {
    val decision = decide(remote = "1.0.1", lastNotifiedTag = "1.0.1", skipIfAlreadyNotified = false)
    assertTrue(decision is UpdateCheckDecision.Available)
    assertFalse((decision as UpdateCheckDecision.Available).notify)
  }

  @Test
  fun unparseableRemoteIsFailed() {
    assertEquals(UpdateCheckDecision.Failed, decide(remote = "nightly"))
  }

  private fun decide(
    fetch: GitHubReleaseFetch? = null,
    remote: String = "1.0.1",
    lastNotifiedTag: String = "",
    notifyEnabled: Boolean = true,
    skipIfAlreadyNotified: Boolean = false,
  ): UpdateCheckDecision {
    return UpdateCheckPolicy.decide(
      installedVersionName = "1.0.0",
      fetch = fetch ?: GitHubReleaseFetch.Ok(
        GitHubRelease(
          tagName = remote,
          htmlUrl = "https://github.com/adamcsk1/wire-pilot/releases/tag/$remote",
        ),
      ),
      lastNotifiedTag = lastNotifiedTag,
      notifyEnabled = notifyEnabled,
      skipIfAlreadyNotified = skipIfAlreadyNotified,
    )
  }
}
