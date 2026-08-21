package com.wirepilot.app.platform

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wirepilot.app.data.SplitTunnelCodec
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel

class SharedPreferencesSplitTunnelStore(
  private val preferences: SharedPreferences,
) : SplitTunnelStore {
  override fun read(tunnelName: String): StoredSplitTunnel {
    return SplitTunnelCodec.decode(preferences.getString(key(tunnelName), null))
  }

  override fun write(tunnelName: String, settings: StoredSplitTunnel) {
    preferences.edit {
      putString(key(tunnelName), SplitTunnelCodec.encode(settings))
    }
  }

  override fun delete(tunnelName: String) {
    preferences.edit {
      remove(key(tunnelName))
    }
  }

  private fun key(tunnelName: String): String {
    return "${PreferenceKeys.SPLIT_TUNNEL_PREFIX}$tunnelName"
  }
}
