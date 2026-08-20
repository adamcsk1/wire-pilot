package com.wirepilot.app.ui

import com.wirepilot.app.R
import com.wirepilot.app.control.SetupStep

object SetupLabels {
  fun stringRes(step: SetupStep): Int {
    return when (step) {
      SetupStep.GRANT_NEARBY_WIFI -> R.string.setup_grant_nearby_wifi
      SetupStep.GRANT_FINE_LOCATION -> R.string.setup_grant_fine_location
      SetupStep.ENABLE_LOCATION -> R.string.setup_enable_location
      SetupStep.GRANT_NOTIFICATIONS -> R.string.setup_grant_notifications
      SetupStep.IMPORT_TUNNEL -> R.string.setup_import_tunnel
      SetupStep.PREPARE_VPN -> R.string.setup_prepare_vpn
    }
  }
}
