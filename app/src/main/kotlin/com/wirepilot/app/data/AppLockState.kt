package com.wirepilot.app.data

data class AppLockState(
  val enabled: Boolean = false,
  val pinSalt: String = "",
  val pinHash: String = "",
  val biometricEnabled: Boolean = false,
  val failedAttempts: Int = 0,
  val lockoutStartedMillis: Long = 0L,
)
