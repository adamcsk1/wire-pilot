package com.wirepilot.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

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

  fun batteryUnrestricted(context: Context): Boolean {
    return context.getSystemService(PowerManager::class.java)
      .isIgnoringBatteryOptimizations(context.packageName)
  }
}
