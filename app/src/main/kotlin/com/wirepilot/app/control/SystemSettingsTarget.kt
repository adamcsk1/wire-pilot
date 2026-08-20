package com.wirepilot.app.control

enum class SystemSettingsTarget {
  APP_INFO,
  BATTERY_OPTIMIZATION,
  UNUSED_APPS,
  LOCATION,
  VPN,
}

object SystemSettingsCatalog {
  fun targets(): List<SystemSettingsTarget> = listOf(
    SystemSettingsTarget.APP_INFO,
    SystemSettingsTarget.BATTERY_OPTIMIZATION,
    SystemSettingsTarget.UNUSED_APPS,
    SystemSettingsTarget.LOCATION,
    SystemSettingsTarget.VPN,
  )

  fun fallback(target: SystemSettingsTarget): SystemSettingsTarget? {
    return if (target == SystemSettingsTarget.APP_INFO) null else SystemSettingsTarget.APP_INFO
  }
}
