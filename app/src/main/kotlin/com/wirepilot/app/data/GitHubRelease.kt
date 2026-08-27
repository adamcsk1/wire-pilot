package com.wirepilot.app.data

data class GitHubRelease(
  val tagName: String,
  val htmlUrl: String,
)

object GitHubReleaseCodec {
  fun parse(body: String): GitHubRelease? {
    val tagName = readFirstString(body, "tag_name")?.trim().orEmpty()
    if (tagName.isEmpty() || tagName.any { character -> character.isISOControl() || character.isWhitespace() }) {
      return null
    }
    val htmlUrl = readPreferredHtmlUrl(body) ?: return null
    return GitHubRelease(tagName = tagName, htmlUrl = htmlUrl)
  }

  fun isTrustedReleaseUrl(url: String): Boolean {
    if (url.any { character -> character.isWhitespace() || character.code < 32 }) {
      return false
    }
    return url.startsWith(TRUSTED_RELEASE_PREFIX)
  }

  private fun readPreferredHtmlUrl(body: String): String? {
    return readAllStrings(body, "html_url").firstOrNull { url -> isTrustedReleaseUrl(url) }
  }

  private fun readFirstString(body: String, key: String): String? {
    return readAllStrings(body, key).firstOrNull()
  }

  private fun readAllStrings(body: String, key: String): List<String> {
    val needle = "\"$key\""
    val values = mutableListOf<String>()
    var searchFrom = 0
    while (searchFrom < body.length) {
      val keyIndex = body.indexOf(needle, searchFrom)
      if (keyIndex < 0) {
        break
      }
      val parsed = readStringValue(body, keyIndex + needle.length)
      if (parsed != null) {
        values += parsed.value
        searchFrom = parsed.nextIndex
      } else {
        searchFrom = keyIndex + 1
      }
    }
    return values
  }

  private fun readStringValue(body: String, afterKey: Int): ParsedString? {
    var cursor = afterKey
    while (cursor < body.length && body[cursor].isWhitespace()) {
      cursor++
    }
    if (cursor >= body.length || body[cursor] != ':') {
      return null
    }
    cursor++
    while (cursor < body.length && body[cursor].isWhitespace()) {
      cursor++
    }
    if (cursor >= body.length || body[cursor] != '"') {
      return null
    }
    cursor++
    val value = StringBuilder()
    while (cursor < body.length) {
      val character = body[cursor]
      if (character == '\\') {
        val escaped = body.getOrNull(cursor + 1) ?: return null
        value.append(unescape(escaped))
        cursor += 2
        continue
      }
      if (character == '"') {
        return ParsedString(value = value.toString(), nextIndex = cursor + 1)
      }
      value.append(character)
      cursor++
    }
    return null
  }

  private fun unescape(escaped: Char): Char {
    return when (escaped) {
      'n' -> '\n'
      'r' -> '\r'
      't' -> '\t'
      else -> escaped
    }
  }

  private data class ParsedString(
    val value: String,
    val nextIndex: Int,
  )

  private const val TRUSTED_RELEASE_PREFIX = "https://github.com/adamcsk1/wire-pilot/releases/"
}
