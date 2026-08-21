package com.wirepilot.app.control

data class AppEntry(
  val packageName: String,
  val label: String,
  val system: Boolean,
  val launchable: Boolean,
)

object AppListFilter {
  fun visible(apps: List<AppEntry>, query: String, showSystem: Boolean): List<AppEntry> {
    val needle = query.trim()
    return apps.filter { app ->
      matchesVisibility(app, showSystem) && matchesQuery(app, needle)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { app -> app.label })
  }

  private fun matchesVisibility(app: AppEntry, showSystem: Boolean): Boolean {
    if (showSystem) {
      return true
    }
    return !app.system || app.launchable
  }

  private fun matchesQuery(app: AppEntry, needle: String): Boolean {
    if (needle.isEmpty()) {
      return true
    }
    return app.label.contains(needle, ignoreCase = true) ||
      app.packageName.contains(needle, ignoreCase = true)
  }
}
