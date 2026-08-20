package com.wirepilot.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.wirepilot.app.control.SsidReadiness

object SsidReadinessReader {
  fun read(context: Context): SsidReadiness {
    val locationManager = context.getSystemService(LocationManager::class.java)
    return SsidReadiness(
      nearbyWifiGranted = granted(context, Manifest.permission.NEARBY_WIFI_DEVICES),
      fineLocationGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
      locationEnabled = locationManager?.isLocationEnabled == true,
    )
  }

  private fun granted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }
}
