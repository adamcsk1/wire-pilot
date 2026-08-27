package com.wirepilot.app.platform

import com.sun.net.httpserver.HttpServer
import com.wirepilot.app.control.GitHubReleaseFetch
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubReleaseClientTest {
  @Test
  fun okParsesTrustedRelease() {
    withServer(200, TRUSTED_BODY) { url ->
      val result = GitHubReleaseClient(url).fetchLatest()
      assertTrue(result is GitHubReleaseFetch.Ok)
      result as GitHubReleaseFetch.Ok
      assertEquals("1.0.1", result.release.tagName)
      assertEquals(
        "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
        result.release.htmlUrl,
      )
    }
  }

  @Test
  fun notFoundIsNotFound() {
    withServer(404, """{"message":"Not Found"}""") { url ->
      assertEquals(GitHubReleaseFetch.NotFound, GitHubReleaseClient(url).fetchLatest())
    }
  }

  @Test
  fun serverErrorIsFailed() {
    withServer(500, "oops") { url ->
      assertEquals(GitHubReleaseFetch.Failed, GitHubReleaseClient(url).fetchLatest())
    }
  }

  @Test
  fun junkBodyIsFailed() {
    withServer(200, "{not-json") { url ->
      assertEquals(GitHubReleaseFetch.Failed, GitHubReleaseClient(url).fetchLatest())
    }
  }

  @Test
  fun connectionRefusedIsFailed() {
    assertEquals(
      GitHubReleaseFetch.Failed,
      GitHubReleaseClient("http://127.0.0.1:1/releases/latest").fetchLatest(),
    )
  }

  private fun withServer(status: Int, body: String, block: (String) -> Unit) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/releases/latest") { exchange ->
      val bytes = body.toByteArray()
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { stream -> stream.write(bytes) }
    }
    server.start()
    try {
      block("http://127.0.0.1:${server.address.port}/releases/latest")
    } finally {
      server.stop(0)
    }
  }

  companion object {
    private const val TRUSTED_BODY =
      """{"tag_name":"1.0.1","html_url":"https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1"}"""
  }
}
