package com.wirepilot.app.platform

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricAvailability {
  fun canAuthenticate(context: Context): Boolean {
    return authenticators(context) != 0
  }

  fun authenticators(context: Context): Int {
    val manager = BiometricManager.from(context)
    if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
      BiometricManager.BIOMETRIC_SUCCESS
    ) {
      return BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
    return 0
  }
}
