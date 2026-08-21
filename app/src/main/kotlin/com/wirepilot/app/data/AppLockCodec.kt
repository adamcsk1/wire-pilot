package com.wirepilot.app.data

object AppLockCodec {
  fun encode(state: AppLockState): String {
    return "${flag(state.enabled)}\t${state.pinSalt}\t${state.pinHash}\t${flag(state.biometricEnabled)}"
  }

  fun decode(raw: String?): AppLockState {
    if (raw.isNullOrEmpty()) {
      return AppLockState()
    }
    val parts = raw.split('\t')
    if (parts.size < 3) {
      return AppLockState()
    }
    return AppLockState(
      enabled = parts[0] == "1",
      pinSalt = parts[1],
      pinHash = parts[2],
      biometricEnabled = parts.getOrNull(3) == "1",
    )
  }

  private fun flag(enabled: Boolean): String {
    return if (enabled) "1" else "0"
  }
}
