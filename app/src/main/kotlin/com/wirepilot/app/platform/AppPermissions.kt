package com.wirepilot.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.VpnService
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.wirepilot.app.control.BackgroundLocationReadiness
import com.wirepilot.app.control.LocationPermissionStep
import com.wirepilot.app.control.SsidReadiness

object AppPermissions {
  fun granted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  fun locationEnabled(context: Context): Boolean {
    return context.getSystemService(LocationManager::class.java).isLocationEnabled
  }

  fun nearbyWifiGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
  }

  fun fineLocationGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
  }

  fun backgroundLocationGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
  }

  fun locationPermissionStep(context: Context): LocationPermissionStep {
    return BackgroundLocationReadiness.permissionStep(fineLocationGranted(context), backgroundLocationGranted(context))
  }

  fun backgroundMonitoringLimited(context: Context, locationForegroundActive: Boolean): Boolean {
    return BackgroundLocationReadiness.monitoringLimited(
      SsidReadiness(nearbyWifiGranted(context), fineLocationGranted(context), locationEnabled(context)),
      backgroundLocationGranted(context),
      locationForegroundActive,
    )
  }

  fun batteryUnrestricted(context: Context): Boolean {
    return context.getSystemService(PowerManager::class.java)
      .isIgnoringBatteryOptimizations(context.packageName)
  }

  fun vpnConsentGranted(context: Context): Boolean {
    return VpnService.prepare(context) == null
  }
}
