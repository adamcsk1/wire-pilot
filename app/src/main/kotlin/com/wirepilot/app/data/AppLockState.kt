package com.wirepilot.app.data

data class AppLockState(
  val enabled: Boolean = false,
  val pinSalt: String = "",
  val pinHash: String = "",
  val biometricEnabled: Boolean = false,
)
