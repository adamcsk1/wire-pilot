package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemSettingsCatalogTest {
  @Test
  fun orderIsAppInfoBatteryUnusedLocationVpn() {
    assertEquals(
      listOf(
        SystemSettingsTarget.APP_INFO,
        SystemSettingsTarget.BATTERY_OPTIMIZATION,
        SystemSettingsTarget.UNUSED_APPS,
        SystemSettingsTarget.LOCATION,
        SystemSettingsTarget.VPN,
      ),
      SystemSettingsCatalog.targets(),
    )
  }

  @Test
  fun nonAppInfoFallsBackToAppInfo() {
    assertEquals(SystemSettingsTarget.APP_INFO, SystemSettingsCatalog.fallback(SystemSettingsTarget.BATTERY_OPTIMIZATION))
    assertEquals(SystemSettingsTarget.APP_INFO, SystemSettingsCatalog.fallback(SystemSettingsTarget.UNUSED_APPS))
    assertEquals(SystemSettingsTarget.APP_INFO, SystemSettingsCatalog.fallback(SystemSettingsTarget.LOCATION))
    assertEquals(SystemSettingsTarget.APP_INFO, SystemSettingsCatalog.fallback(SystemSettingsTarget.VPN))
  }

  @Test
  fun appInfoHasNoFallback() {
    assertNull(SystemSettingsCatalog.fallback(SystemSettingsTarget.APP_INFO))
  }
}
