package com.wirepilot.app.control

object WireGuardContract {
  const val PACKAGE_NAME = "com.wireguard.android"
  const val PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"
  const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
  const val ACTION_SET_TUNNEL_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
  const val EXTRA_TUNNEL = "tunnel"
  const val RECEIVER_CLASS_NAME = "com.wireguard.android.model.TunnelManager\$IntentReceiver"
}
