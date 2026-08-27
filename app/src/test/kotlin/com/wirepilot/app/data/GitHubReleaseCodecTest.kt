package com.wirepilot.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubReleaseCodecTest {
  @Test
  fun parsesTopLevelTagAndReleaseUrl() {
    val body = """
      {
        "html_url": "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
        "author": { "html_url": "https://github.com/adamcsk1" },
        "tag_name": "1.0.1"
      }
    """.trimIndent()
    assertEquals(
      GitHubRelease(
        tagName = "1.0.1",
        htmlUrl = "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
      ),
      GitHubReleaseCodec.parse(body),
    )
  }

  @Test
  fun prefersReleaseHtmlUrlOverAuthor() {
    val body = """
      {
        "author": { "html_url": "https://github.com/adamcsk1" },
        "html_url": "https://github.com/adamcsk1/wire-pilot/releases/tag/v1.0.1",
        "tag_name": "v1.0.1"
      }
    """.trimIndent()
    assertEquals(
      "https://github.com/adamcsk1/wire-pilot/releases/tag/v1.0.1",
      GitHubReleaseCodec.parse(body)?.htmlUrl,
    )
  }

  @Test
  fun unescapesQuotedStrings() {
    val body = """{"tag_name":"1.0.1","html_url":"https:\/\/github.com\/adamcsk1\/wire-pilot\/releases\/tag\/1.0.1"}"""
    assertEquals(
      "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
      GitHubReleaseCodec.parse(body)?.htmlUrl,
    )
  }

  @Test
  fun rejectsControlCharactersAndWhitespaceInTag() {
    val url = "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1"
    assertNull(GitHubReleaseCodec.parse("""{"tag_name":"1\n0.1","html_url":"$url"}"""))
    assertNull(GitHubReleaseCodec.parse("""{"tag_name":"1.0.2-\nevil","html_url":"$url"}"""))
    assertNull(GitHubReleaseCodec.parse("""{"tag_name":"1.0.1 rc","html_url":"$url"}"""))
    assertEquals(
      "1.0.1-rc.1",
      GitHubReleaseCodec.parse("""{"tag_name":"1.0.1-rc.1","html_url":"$url"}""")?.tagName,
    )
  }

  @Test
  fun skipsKeysWithoutStringValues() {
    val body = """{"tag_name":1,"tag_name":"1.0.1","html_url":["x"],"html_url":"https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1"}"""
    assertEquals(
      GitHubRelease(
        tagName = "1.0.1",
        htmlUrl = "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
      ),
      GitHubReleaseCodec.parse(body),
    )
  }

  @Test
  fun rejectsUntrustedReleaseUrls() {
    assertNull(
      GitHubReleaseCodec.parse(
        """{"tag_name":"1.0.1","html_url":"https://example.com/releases/tag/1.0.1"}""",
      ),
    )
    assertNull(
      GitHubReleaseCodec.parse(
        """{"tag_name":"1.0.1","html_url":"http://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1"}""",
      ),
    )
    assertEquals(
      false,
      GitHubReleaseCodec.isTrustedReleaseUrl("https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1\n"),
    )
  }

  @Test
  fun rejectsMissingFields() {
    assertNull(GitHubReleaseCodec.parse("""{"tag_name":"1.0.1"}"""))
    assertNull(GitHubReleaseCodec.parse("""{"html_url":"https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1"}"""))
    assertNull(GitHubReleaseCodec.parse("""{"tag_name":"1.0.1","html_url":"unterminated"""))
    assertNull(GitHubReleaseCodec.parse("{\"tag_name\":\"1.0.1\",\"html_url\":\"x\\"))
    assertNull(GitHubReleaseCodec.parse(""))
  }
}
