package com.wirepilot.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.wirepilot.app.R
import com.wirepilot.app.control.UpdateNotifyGate
import com.wirepilot.app.data.GitHubReleaseCodec

class UpdateNotifier(
  private val context: Context,
) {
  fun show(tagName: String, htmlUrl: String): Boolean {
    if (!GitHubReleaseCodec.isTrustedReleaseUrl(htmlUrl)) {
      return false
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    ensureChannel(manager)
    val channel = manager.getNotificationChannel(CHANNEL_ID)
    val importance = channel?.importance ?: UpdateNotifyGate.IMPORTANCE_NONE
    if (!UpdateNotifyGate.canPost(manager.areNotificationsEnabled(), importance)) {
      return false
    }
    val contentIntent = PendingIntent.getActivity(
      context,
      REQUEST_OPEN_RELEASE,
      Intent(Intent.ACTION_VIEW, htmlUrl.toUri()),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_monitor)
      .setContentTitle(context.getString(R.string.update_notification_title))
      .setContentText(context.getString(R.string.update_notification_text, tagName))
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .build()
    return try {
      manager.notify(NOTIFICATION_ID, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  private fun ensureChannel(manager: NotificationManager) {
    val channel = NotificationChannel(
      CHANNEL_ID,
      context.getString(R.string.update_channel_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = context.getString(R.string.update_channel_description)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 42
    private const val REQUEST_OPEN_RELEASE = 43
  }
}
