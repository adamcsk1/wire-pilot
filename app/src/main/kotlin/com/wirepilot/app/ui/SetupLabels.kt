package com.wirepilot.app.ui

import com.wirepilot.app.R
import com.wirepilot.app.control.SetupStep

object SetupLabels {
  fun stringRes(step: SetupStep): Int {
    return when (step) {
      SetupStep.INSTALL_WIREGUARD -> R.string.setup_install_wireguard
      SetupStep.GRANT_CONTROL -> R.string.setup_grant_control
      SetupStep.ENABLE_REMOTE_CONTROL -> R.string.setup_enable_remote_control
      SetupStep.GRANT_NEARBY_WIFI -> R.string.setup_grant_nearby_wifi
      SetupStep.GRANT_FINE_LOCATION -> R.string.setup_grant_fine_location
      SetupStep.ENABLE_LOCATION -> R.string.setup_enable_location
      SetupStep.GRANT_NOTIFICATIONS -> R.string.setup_grant_notifications
      SetupStep.SET_TUNNEL_NAME -> R.string.setup_set_tunnel_name
    }
  }
}
