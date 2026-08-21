package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemSettingsCatalogTest {
  @Test
  fun groupsRequiredReliabilityAlwaysOn() {
    assertEquals(listOf(SystemSettingsTarget.LOCATION), SystemSettingsCatalog.required())
    assertEquals(
      listOf(
        SystemSettingsTarget.APP_INFO,
        SystemSettingsTarget.BATTERY_OPTIMIZATION,
        SystemSettingsTarget.UNUSED_APPS,
      ),
      SystemSettingsCatalog.reliability(),
    )
    assertEquals(listOf(SystemSettingsTarget.VPN), SystemSettingsCatalog.alwaysOn())
    assertEquals(
      SystemSettingsCatalog.required() + SystemSettingsCatalog.reliability() + SystemSettingsCatalog.alwaysOn(),
      SystemSettingsCatalog.targets(),
    )
    assertEquals(SystemSettingsGroup.REQUIRED, SystemSettingsCatalog.group(SystemSettingsTarget.LOCATION))
    assertEquals(SystemSettingsGroup.RELIABILITY, SystemSettingsCatalog.group(SystemSettingsTarget.APP_INFO))
    assertEquals(SystemSettingsGroup.RELIABILITY, SystemSettingsCatalog.group(SystemSettingsTarget.BATTERY_OPTIMIZATION))
    assertEquals(SystemSettingsGroup.RELIABILITY, SystemSettingsCatalog.group(SystemSettingsTarget.UNUSED_APPS))
    assertEquals(SystemSettingsGroup.ALWAYS_ON, SystemSettingsCatalog.group(SystemSettingsTarget.VPN))
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
