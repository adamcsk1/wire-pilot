package com.wirepilot.app.control

import com.wirepilot.app.data.GitHubRelease

object UpdateCheckPolicy {
  fun decide(
    installedVersionName: String,
    fetch: GitHubReleaseFetch,
    lastNotifiedTag: String,
    notifyEnabled: Boolean,
    skipIfAlreadyNotified: Boolean,
  ): UpdateCheckDecision {
    return when (fetch) {
      GitHubReleaseFetch.Failed -> UpdateCheckDecision.Failed
      GitHubReleaseFetch.NotFound -> UpdateCheckDecision.NoRelease
      is GitHubReleaseFetch.Ok -> decideRelease(
        installedVersionName = installedVersionName,
        release = fetch.release,
        lastNotifiedTag = lastNotifiedTag,
        notifyEnabled = notifyEnabled,
        skipIfAlreadyNotified = skipIfAlreadyNotified,
      )
    }
  }

  private fun decideRelease(
    installedVersionName: String,
    release: GitHubRelease,
    lastNotifiedTag: String,
    notifyEnabled: Boolean,
    skipIfAlreadyNotified: Boolean,
  ): UpdateCheckDecision {
    val newer = VersionComparator.isNewer(release.tagName, installedVersionName) ?: return UpdateCheckDecision.Failed
    if (!newer) {
      return UpdateCheckDecision.UpToDate
    }
    val alreadyNotified = VersionComparator.compare(release.tagName, lastNotifiedTag) == 0
    if (alreadyNotified && skipIfAlreadyNotified) {
      return UpdateCheckDecision.AlreadyNotified
    }
    return UpdateCheckDecision.Available(
      tagName = release.tagName,
      htmlUrl = release.htmlUrl,
      notify = notifyEnabled && !alreadyNotified,
    )
  }
}
