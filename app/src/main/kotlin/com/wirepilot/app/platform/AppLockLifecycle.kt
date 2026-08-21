package com.wirepilot.app.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wirepilot.app.LockActivity
import com.wirepilot.app.control.AppLockPolicy
import com.wirepilot.app.control.AppLockSession

class AppLockLifecycle(
  private val session: AppLockSession,
  private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
  private var backgroundedAtMillis: Long? = null

  fun register(application: Application) {
    application.registerActivityLifecycleCallbacks(this)
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
  }

  override fun onStart(owner: LifecycleOwner) {
    val leftAt = backgroundedAtMillis
    backgroundedAtMillis = null
    if (leftAt != null &&
      AppLockPolicy.shouldLockAfterBackground(elapsedRealtime() - leftAt)
    ) {
      session.lock()
    }
  }

  override fun onStop(owner: LifecycleOwner) {
    backgroundedAtMillis = elapsedRealtime()
  }

  override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
    applySecureFlag(activity)
    presentChallenge(activity)
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    applySecureFlag(activity)
  }

  override fun onActivityStarted(activity: Activity) {
    applySecureFlag(activity)
    presentChallenge(activity)
  }

  override fun onActivityResumed(activity: Activity) {
    applySecureFlag(activity)
    presentChallenge(activity)
  }

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit

  private fun presentChallenge(activity: Activity) {
    if (activity is LockActivity || activity.isFinishing) {
      return
    }
    if (!session.needsChallenge()) {
      return
    }
    activity.startActivity(
      LockActivity.intent(activity)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
  }

  private fun applySecureFlag(activity: Activity) {
    if (session.isEnabled() || activity is LockActivity) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }
}
