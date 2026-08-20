package com.wirepilot.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.wirepilot.app.control.SystemSettingsCatalog
import com.wirepilot.app.control.SystemSettingsTarget

class SettingsNavigator(
  private val context: Context,
) {
  fun open(target: SystemSettingsTarget): Boolean {
    val chain = buildList {
      add(target)
      val fallback = SystemSettingsCatalog.fallback(target)
      if (fallback != null) {
        add(fallback)
      }
    }
    return chain.any(::launch)
  }

  private fun launch(target: SystemSettingsTarget): Boolean {
    val intent = intentFor(target) ?: return false
    return try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }

  private fun intentFor(target: SystemSettingsTarget): Intent? {
    val packageName = context.packageName
    return when (target) {
      SystemSettingsTarget.APP_INFO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
      }
      SystemSettingsTarget.BATTERY_OPTIMIZATION ->
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      SystemSettingsTarget.UNUSED_APPS -> runCatching {
        IntentCompat.createManageUnusedAppRestrictionsIntent(context, packageName)
      }.getOrNull()
      SystemSettingsTarget.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
  }
}
