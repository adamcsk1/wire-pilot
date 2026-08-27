package com.wirepilot.app.control

import com.wirepilot.app.data.GitHubRelease

sealed class GitHubReleaseFetch {
  data class Ok(val release: GitHubRelease) : GitHubReleaseFetch()
  data object NotFound : GitHubReleaseFetch()
  data object Failed : GitHubReleaseFetch()
}
