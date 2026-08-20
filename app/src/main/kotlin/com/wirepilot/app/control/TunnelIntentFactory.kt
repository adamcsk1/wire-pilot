package com.wirepilot.app.control

data class TunnelIntentSpec(
  val action: String,
  val packageName: String,
  val receiverClassName: String,
  val extras: Map<String, String>,
  val includeStoppedPackages: Boolean,
)

object TunnelIntentFactory {
  fun create(tunnelName: String, command: TunnelCommand): TunnelIntentSpec {
    val action = when (command) {
      TunnelCommand.UP -> WireGuardContract.ACTION_SET_TUNNEL_UP
      TunnelCommand.DOWN -> WireGuardContract.ACTION_SET_TUNNEL_DOWN
    }
    return TunnelIntentSpec(
      action = action,
      packageName = WireGuardContract.PACKAGE_NAME,
      receiverClassName = WireGuardContract.RECEIVER_CLASS_NAME,
      extras = mapOf(WireGuardContract.EXTRA_TUNNEL to tunnelName),
      includeStoppedPackages = true,
    )
  }
}
