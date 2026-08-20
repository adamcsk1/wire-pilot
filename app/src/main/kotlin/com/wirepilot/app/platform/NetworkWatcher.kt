package com.wirepilot.app.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.wirepilot.app.receiver.NetworkChangeReceiver

class NetworkWatcher(
  private val context: Context,
  private val inventory: NetworkInventory,
  private val onNetworkChanged: () -> Unit,
) {
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  private val liveCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      val capabilities = connectivityManager.getNetworkCapabilities(network)
      if (capabilities != null) {
        inventory.put(network, capabilities)
      }
      onNetworkChanged()
    }

    override fun onLost(network: Network) {
      inventory.remove(network)
      onNetworkChanged()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
      inventory.put(network, networkCapabilities)
      onNetworkChanged()
    }
  }

  fun register() {
    runCatching { connectivityManager.unregisterNetworkCallback(wifiPendingIntent()) }
    runCatching { connectivityManager.unregisterNetworkCallback(cellularPendingIntent()) }
    runCatching { connectivityManager.unregisterNetworkCallback(liveCallback) }
    connectivityManager.registerNetworkCallback(wifiRequest(), wifiPendingIntent())
    connectivityManager.registerNetworkCallback(cellularRequest(), cellularPendingIntent())
    connectivityManager.registerNetworkCallback(openRequest(), liveCallback)
  }

  private fun wifiRequest(): NetworkRequest {
    return NetworkRequest.Builder()
      .clearCapabilities()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .build()
  }

  private fun cellularRequest(): NetworkRequest {
    return NetworkRequest.Builder()
      .clearCapabilities()
      .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
      .build()
  }

  private fun openRequest(): NetworkRequest {
    return NetworkRequest.Builder()
      .clearCapabilities()
      .build()
  }

  private fun wifiPendingIntent(): PendingIntent {
    return pendingIntent(REQUEST_WIFI)
  }

  private fun cellularPendingIntent(): PendingIntent {
    return pendingIntent(REQUEST_CELLULAR)
  }

  private fun pendingIntent(requestCode: Int): PendingIntent {
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      Intent(context, NetworkChangeReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
  }

  companion object {
    private const val REQUEST_WIFI = 21
    private const val REQUEST_CELLULAR = 22
  }
}
