package com.wirepilot.app.control

object ConfigZipNames {
  fun tunnelNameFromPath(path: String): String? {
    var name = path.trim()
    val slash = name.lastIndexOf('/')
    if (slash >= 0) {
      if (slash >= name.length - 1) {
        return null
      }
      name = name.substring(slash + 1)
    }
    if (!name.endsWith(".conf", ignoreCase = true)) {
      return null
    }
    name = name.substring(0, name.length - ".conf".length).trim()
    return name.takeIf { it.isNotBlank() && isValidTunnelName(it) }
  }

  fun fileName(tunnelName: String): String {
    return "$tunnelName.conf"
  }

  fun isValidTunnelName(name: String): Boolean {
    return NAME_PATTERN.matches(name)
  }

  private val NAME_PATTERN = Regex("^[A-Za-z0-9_=+.-]{1,15}$")
}
