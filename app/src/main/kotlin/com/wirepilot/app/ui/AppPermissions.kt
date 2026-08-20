package com.wirepilot.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.wirepilot.app.control.WireGuardContract

object AppPermissions {
  fun granted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  fun locationEnabled(context: Context): Boolean {
    return context.getSystemService(LocationManager::class.java).isLocationEnabled
  }

  fun wireGuardInstalled(context: Context): Boolean {
    return try {
      context.packageManager.getPackageInfo(
        WireGuardContract.PACKAGE_NAME,
        PackageManager.PackageInfoFlags.of(0),
      )
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  fun nearbyWifiGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
  }

  fun fineLocationGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
  }

  fun notificationsGranted(context: Context): Boolean {
    return granted(context, Manifest.permission.POST_NOTIFICATIONS)
  }

  fun controlGranted(context: Context): Boolean {
    return granted(context, WireGuardContract.PERMISSION)
  }
}
