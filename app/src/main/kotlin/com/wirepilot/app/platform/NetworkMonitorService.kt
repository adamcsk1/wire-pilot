package com.wirepilot.app.platform

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wirepilot.app.SettingsActivity
import com.wirepilot.app.R
import com.wirepilot.app.WirePilotApp
import com.wirepilot.app.control.NetworkMonitorServiceRuntime
import com.wirepilot.app.control.NetworkMonitorServiceStart

class NetworkMonitorService : Service() {
  private val container
    get() = (application as WirePilotApp).container
  private val locationModeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      container.networkMonitorCoordinator.reconcile()
    }
  }
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
    ContextCompat.registerReceiver(
      this,
      locationModeReceiver,
      IntentFilter(LocationManager.MODE_CHANGED_ACTION),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!enterForeground(foregroundTypes()) &&
      !enterForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    ) {
      container.locationForegroundActive = false
      stopSelf(startId)
      return START_NOT_STICKY
    }
    val mode = container.networkMonitorCoordinator.currentMode()
    return when (runtime.onStart(mode, startId)) {
      NetworkMonitorServiceStart.STICKY -> START_STICKY
      NetworkMonitorServiceStart.NOT_STICKY -> START_NOT_STICKY
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    unregisterReceiver(locationModeReceiver)
    container.locationForegroundActive = false
    super.onDestroy()
  }

  private fun enterForeground(types: Int): Boolean {
    return try {
      startForeground(NOTIFICATION_ID, notification(), types)
      container.locationForegroundActive = types and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0
      true
    } catch (_: SecurityException) {
      false
    } catch (_: ForegroundServiceStartNotAllowedException) {
      false
    }
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
      Intent(this, SettingsActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_monitor)
      .setContentTitle(getString(R.string.monitor_notification_title))
      .setContentText(getString(
        if (AppPermissions.backgroundMonitoringLimited(this, container.locationForegroundActive)) {
          R.string.monitor_notification_limited
        } else {
          R.string.monitor_notification_watching
        },
      ))
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
