package com.wirepilot.app.platform

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

class WatchingService : Service() {
  override fun onCreate() {
    super.onCreate()
    ensureChannel()
    startForeground(
      NOTIFICATION_ID,
      notification(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
    val container = (application as WirePilotApp).container
    container.debouncer.preferInProcess = true
    container.networkWatcher.register()
  }

  override fun onDestroy() {
    (application as WirePilotApp).container.debouncer.preferInProcess = false
    super.onDestroy()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun ensureChannel() {
    val manager = getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.watching_channel_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = getString(R.string.watching_channel_description)
      setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
  }

  private fun notification(): Notification {
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_watching)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.watching_notification_text))
      .setContentIntent(contentIntent)
      .setOngoing(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  companion object {
    private const val CHANNEL_ID = "watching_visible"
    private const val NOTIFICATION_ID = 41

    fun start(context: Context) {
      ContextCompat.startForegroundService(context, Intent(context, WatchingService::class.java))
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, WatchingService::class.java))
    }
  }
}
