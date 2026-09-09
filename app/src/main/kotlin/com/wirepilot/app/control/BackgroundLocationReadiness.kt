package com.wirepilot.app.control

enum class LocationPermissionStep {
  PRECISE_LOCATION,
  BACKGROUND_LOCATION,
  COMPLETE,
}

object BackgroundLocationReadiness {
  fun permissionStep(fineLocationGranted: Boolean, backgroundLocationGranted: Boolean): LocationPermissionStep {
    if (!fineLocationGranted) return LocationPermissionStep.PRECISE_LOCATION
    if (!backgroundLocationGranted) return LocationPermissionStep.BACKGROUND_LOCATION
    return LocationPermissionStep.COMPLETE
  }

  fun monitoringLimited(
    readiness: SsidReadiness,
    backgroundLocationGranted: Boolean,
    locationForegroundActive: Boolean,
  ): Boolean {
    return SsidReadinessEvaluator.blocker(readiness) != null ||
      !backgroundLocationGranted || !locationForegroundActive
  }
}
