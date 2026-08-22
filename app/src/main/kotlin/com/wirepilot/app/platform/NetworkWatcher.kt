package com.wirepilot.app.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.wirepilot.app.receiver.NetworkChangeReceiver

class NetworkWatcher(
  private val context: Context,
  private val inventory: NetworkInventory,
  private val onNetworkChanged: () -> Unit,
) {
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  private val callbackHandler = Handler(Looper.getMainLooper())
  private var fallbacksRegistered = false
  private var liveRegistered = false
  private val openLiveCallback = LocationAwareCallback(removeOnLost = true)
  private val wifiLiveCallback = LocationAwareCallback(removeOnLost = false)

  @Synchronized
  fun registerFallbacks() {
    if (fallbacksRegistered) {
      return
    }
    runCatching { connectivityManager.unregisterNetworkCallback(openPendingIntent()) }
    connectivityManager.registerNetworkCallback(openRequest(), openPendingIntent())
    fallbacksRegistered = true
  }

  @Synchronized
  fun unregisterFallbacks() {
    runCatching { connectivityManager.unregisterNetworkCallback(openPendingIntent()) }
    fallbacksRegistered = false
  }

  @Synchronized
  fun startLive() {
    if (liveRegistered) {
      return
    }
    registerLiveCallbacks()
    liveRegistered = true
  }

  @Synchronized
  fun restartLive() {
    liveRegistered = false
    registerLiveCallbacks()
    liveRegistered = true
  }

  private fun registerLiveCallbacks() {
    runCatching { connectivityManager.unregisterNetworkCallback(openLiveCallback) }
    runCatching { connectivityManager.unregisterNetworkCallback(wifiLiveCallback) }
    connectivityManager.registerNetworkCallback(openRequest(), openLiveCallback, callbackHandler)
    connectivityManager.registerNetworkCallback(wifiRequest(), wifiLiveCallback, callbackHandler)
  }

  private fun openRequest(): NetworkRequest {
    return NetworkRequest.Builder()
      .clearCapabilities()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
      .build()
  }

  private fun wifiRequest(): NetworkRequest {
    return NetworkRequest.Builder()
      .clearCapabilities()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .build()
  }

  private inner class LocationAwareCallback(
    private val removeOnLost: Boolean,
  ) : ConnectivityManager.NetworkCallback(
    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
  ) {
    override fun onAvailable(network: Network) {
      onNetworkChanged()
    }

    override fun onLost(network: Network) {
      if (removeOnLost) {
        inventory.remove(network)
      }
      onNetworkChanged()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
      inventory.observeCallback(network, networkCapabilities)
      onNetworkChanged()
    }
  }

  private fun openPendingIntent(): PendingIntent {
    return pendingIntent(REQUEST_OPEN)
  }

  private fun pendingIntent(requestCode: Int): PendingIntent {
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      Intent(context, NetworkChangeReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  companion object {
    private const val REQUEST_OPEN = 23
  }
}
