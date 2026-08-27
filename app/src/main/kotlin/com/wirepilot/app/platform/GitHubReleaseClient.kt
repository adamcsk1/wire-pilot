package com.wirepilot.app.platform

import com.wirepilot.app.control.GitHubReleaseFetch
import com.wirepilot.app.data.GitHubReleaseCodec
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(
  private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
  private val lock = Any()
  private var inFlight: HttpURLConnection? = null

  fun cancel() {
    synchronized(lock) {
      inFlight?.disconnect()
      inFlight = null
    }
  }

  fun fetchLatest(): GitHubReleaseFetch {
    val connection = (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
      connectTimeout = CONNECT_TIMEOUT_MS
      readTimeout = READ_TIMEOUT_MS
      requestMethod = "GET"
      instanceFollowRedirects = true
      useCaches = false
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", USER_AGENT)
    }
    synchronized(lock) {
      inFlight = connection
    }
    return try {
      when (connection.responseCode) {
        HttpURLConnection.HTTP_OK -> parseBody(connection)
        HttpURLConnection.HTTP_NOT_FOUND -> GitHubReleaseFetch.NotFound
        else -> GitHubReleaseFetch.Failed
      }
    } catch (_: Exception) {
      GitHubReleaseFetch.Failed
    } finally {
      synchronized(lock) {
        if (inFlight === connection) {
          inFlight = null
        }
      }
      connection.disconnect()
    }
  }

  private fun parseBody(connection: HttpURLConnection): GitHubReleaseFetch {
    val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
    val release = GitHubReleaseCodec.parse(body) ?: return GitHubReleaseFetch.Failed
    return GitHubReleaseFetch.Ok(release)
  }

  companion object {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/adamcsk1/wire-pilot/releases/latest"
    const val CONNECT_TIMEOUT_MS = 2_500
    const val READ_TIMEOUT_MS = 4_000
    private const val USER_AGENT = "WirePilot"
  }
}
