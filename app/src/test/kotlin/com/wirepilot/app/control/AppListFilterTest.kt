package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class AppListFilterTest {
  private val maps = AppEntry("com.maps", "Maps", system = false, launchable = true)
  private val settings = AppEntry("com.android.settings", "Settings", system = true, launchable = true)
  private val hidden = AppEntry("com.android.hidden", "Hidden", system = true, launchable = false)
  private val beta = AppEntry("com.beta", "Beta", system = false, launchable = false)

  @Test
  fun hidesNonLaunchableSystemUnlessShown() {
    val apps = listOf(maps, settings, hidden, beta)
    assertEquals(
      listOf(beta, maps, settings),
      AppListFilter.visible(apps, query = "", showSystem = false),
    )
    assertEquals(
      listOf(beta, hidden, maps, settings),
      AppListFilter.visible(apps, query = "", showSystem = true),
    )
  }

  @Test
  fun queryMatchesLabelOrPackage() {
    val apps = listOf(maps, settings, beta)
    assertEquals(listOf(maps), AppListFilter.visible(apps, "map", showSystem = false))
    assertEquals(listOf(beta), AppListFilter.visible(apps, "com.beta", showSystem = false))
    assertEquals(emptyList(), AppListFilter.visible(apps, "nope", showSystem = false))
  }
}
