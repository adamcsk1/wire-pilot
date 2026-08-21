package com.wirepilot.app.control

enum class SystemSettingsTarget {
  APP_INFO,
  BATTERY_OPTIMIZATION,
  UNUSED_APPS,
  LOCATION,
  VPN,
}

enum class SystemSettingsGroup {
  REQUIRED,
  RELIABILITY,
  ALWAYS_ON,
}

object SystemSettingsCatalog {
  fun required(): List<SystemSettingsTarget> = listOf(SystemSettingsTarget.LOCATION)

  fun reliability(): List<SystemSettingsTarget> = listOf(
    SystemSettingsTarget.APP_INFO,
    SystemSettingsTarget.BATTERY_OPTIMIZATION,
    SystemSettingsTarget.UNUSED_APPS,
  )

  fun alwaysOn(): List<SystemSettingsTarget> = listOf(SystemSettingsTarget.VPN)

  fun targets(): List<SystemSettingsTarget> = required() + reliability() + alwaysOn()

  fun group(target: SystemSettingsTarget): SystemSettingsGroup {
    return when (target) {
      SystemSettingsTarget.LOCATION -> SystemSettingsGroup.REQUIRED
      SystemSettingsTarget.APP_INFO,
      SystemSettingsTarget.BATTERY_OPTIMIZATION,
      SystemSettingsTarget.UNUSED_APPS,
      -> SystemSettingsGroup.RELIABILITY
      SystemSettingsTarget.VPN -> SystemSettingsGroup.ALWAYS_ON
    }
  }

  fun fallback(target: SystemSettingsTarget): SystemSettingsTarget? {
    return if (target == SystemSettingsTarget.APP_INFO) null else SystemSettingsTarget.APP_INFO
  }
}
