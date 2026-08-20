package com.wirepilot.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.wirepilot.app.control.WireGuardContract

object WireGuardLauncher {
  fun openOrStore(context: Context) {
    if (AppPermissions.wireGuardInstalled(context)) {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(WireGuardContract.PACKAGE_NAME)
      if (launchIntent != null) {
        context.startActivity(launchIntent)
        return
      }
    }
    val market = "market://details?id=${WireGuardContract.PACKAGE_NAME}".toUri()
    val play = Uri.parse("https://play.google.com/store/apps/details?id=${WireGuardContract.PACKAGE_NAME}")
    val marketIntent = Intent(Intent.ACTION_VIEW, market)
    if (marketIntent.resolveActivity(context.packageManager) != null) {
      context.startActivity(marketIntent)
    } else {
      context.startActivity(Intent(Intent.ACTION_VIEW, play))
    }
  }
}
