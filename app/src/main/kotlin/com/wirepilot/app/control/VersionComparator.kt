package com.wirepilot.app.control

object VersionComparator {
  data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
  ) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
      return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    }
  }

  fun parse(raw: String): SemanticVersion? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
      return null
    }
    val withoutPrefix = if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
      trimmed.substring(1)
    } else {
      trimmed
    }
    val core = withoutPrefix.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    if (parts.size !in 2..3) {
      return null
    }
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    val patch = if (parts.size == 3) {
      parts[2].toIntOrNull() ?: return null
    } else {
      0
    }
    if (major < 0 || minor < 0 || patch < 0) {
      return null
    }
    return SemanticVersion(major = major, minor = minor, patch = patch)
  }

  fun compare(left: String, right: String): Int? {
    val leftVersion = parse(left) ?: return null
    val rightVersion = parse(right) ?: return null
    return leftVersion.compareTo(rightVersion)
  }

  fun isNewer(remote: String, installed: String): Boolean? {
    val remoteVersion = parse(remote) ?: return null
    val installedVersion = parse(installed) ?: return true
    return remoteVersion > installedVersion
  }
}
