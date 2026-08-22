package com.wirepilot.app.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wirepilot.app.MainActivity
import com.wirepilot.app.R
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.NetworkMonitorMode
import com.wirepilot.app.control.NetworkMonitorServiceRuntime
import com.wirepilot.app.control.NetworkMonitorServiceStart
import com.wirepilot.app.ui.AppPermissions

class NetworkMonitorService : Service() {
  private val container
    get() = (application as WirePilotApp).container
  private val runtime by lazy {
    NetworkMonitorServiceRuntime(
      registerFallbacks = { container.networkWatcher.registerFallbacks() },
      unregisterFallbacks = { container.networkWatcher.unregisterFallbacks() },
      restartLive = { container.networkWatcher.restartLive() },
      updateNotification = {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
      },
      stopService = { startId -> stopSelf(startId) },
    )
  }

  override fun onCreate() {
    super.onCreate()
    ensureChannel()
    enterForeground()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    enterForeground()
    val mode = container.networkMonitorCoordinator.currentMode()
    return when (runtime.onStart(mode, startId)) {
      NetworkMonitorServiceStart.STICKY -> START_STICKY
      NetworkMonitorServiceStart.NOT_STICKY -> START_NOT_STICKY
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun enterForeground() {
    try {
      startForeground(NOTIFICATION_ID, notification(), foregroundTypes())
    } catch (_: SecurityException) {
      enterSpecialUseForeground()
    } catch (_: ForegroundServiceStartNotAllowedException) {
      enterSpecialUseForeground()
    }
  }

  private fun enterSpecialUseForeground() {
    startForeground(
      NOTIFICATION_ID,
      notification(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }

  private fun foregroundTypes(): Int {
    val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    return if (AppPermissions.fineLocationGranted(this)) {
      types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else {
      types
    }
  }

  private fun ensureChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.monitor_channel_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = getString(R.string.monitor_channel_description)
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun notification(): Notification {
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_monitor)
      .setContentTitle(getString(R.string.monitor_notification_title))
      .setContentText(getString(R.string.monitor_notification_watching))
      .setContentIntent(contentIntent)
      .setOngoing(true)
      .setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  companion object {
    private const val CHANNEL_ID = "network_monitor"
    private const val NOTIFICATION_ID = 41

    fun start(context: Context) {
      ContextCompat.startForegroundService(context, Intent(context, NetworkMonitorService::class.java))
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, NetworkMonitorService::class.java))
    }
  }
}
