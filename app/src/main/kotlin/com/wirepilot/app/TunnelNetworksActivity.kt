package com.wirepilot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.SsidBlocker
import com.wirepilot.app.control.SsidReadiness
import com.wirepilot.app.control.SsidReadinessEvaluator
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SystemBarInsets

class TunnelNetworksActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var tunnelName: String
  private lateinit var ssidList: LinearLayout
  private lateinit var emptySsids: TextView
  private lateinit var addCurrentButton: MaterialButton
  private lateinit var connectOnMobileSwitch: MaterialSwitch
  private var suppressMobileSwitch = false

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    refreshUi()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME).orEmpty()
    if (tunnelName.isBlank()) {
      finish()
      return
    }
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.activity_tunnel_networks)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    val toolbar = findViewById<MaterialToolbar>(R.id.networksToolbar)
    toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    toolbar.subtitle = tunnelName
    ssidList = findViewById(R.id.ssidList)
    emptySsids = findViewById(R.id.emptySsids)
    addCurrentButton = findViewById(R.id.addCurrentButton)
    addCurrentButton.setOnClickListener { onAddCurrentOrFixSsid() }
    findViewById<MaterialButton>(R.id.addSsidButton).setOnClickListener { showAddSsidDialog() }
    connectOnMobileSwitch = findViewById(R.id.connectOnMobileSwitch)
    connectOnMobileSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressMobileSwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setConnectOnMobile(checked, tunnelName)
      refreshUi()
    }
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  private fun refreshUi() {
    suppressMobileSwitch = true
    connectOnMobileSwitch.isChecked = controller.viewState().mobileTunnelName == tunnelName
    suppressMobileSwitch = false
    val ssids = controller.excludedSsids(tunnelName).sorted()
    ssidList.removeAllViews()
    emptySsids.isVisible = ssids.isEmpty()
    ssids.forEach { ssid ->
      val row = layoutInflater.inflate(R.layout.item_ssid, ssidList, false)
      row.findViewById<TextView>(R.id.ssidName).text = ssid
      row.findViewById<MaterialButton>(R.id.removeSsidButton).setOnClickListener {
        controller.removeExcludedSsid(ssid, tunnelName)
        refreshUi()
      }
      ssidList.addView(row)
    }
    bindSsidAction(ssids)
  }

  private fun bindSsidAction(excluded: List<String>) {
    val snapshot = (application as WirePilotApp).container.ssidReader.snapshot()
    val currentSsid = snapshot.wifiSsids.firstOrNull()
    if (currentSsid != null) {
      addCurrentButton.isEnabled = currentSsid !in excluded
      addCurrentButton.text = getString(R.string.add_current_ssid, currentSsid)
      return
    }
    if (!snapshot.connectedToWifi) {
      addCurrentButton.isEnabled = false
      addCurrentButton.text = getString(R.string.add_current_ssid_unavailable)
      return
    }
    when (ssidBlockerWhenUnreadable()) {
      SsidBlocker.NEARBY_WIFI_PERMISSION -> {
        addCurrentButton.isEnabled = true
        addCurrentButton.text = getString(R.string.grant_nearby_wifi)
      }
      SsidBlocker.FINE_LOCATION_PERMISSION -> {
        addCurrentButton.isEnabled = true
        addCurrentButton.text = getString(R.string.grant_fine_location)
      }
      SsidBlocker.LOCATION_OFF -> {
        addCurrentButton.isEnabled = true
        addCurrentButton.text = getString(R.string.turn_on_location)
      }
      SsidBlocker.UNKNOWN_NETWORK -> {
        addCurrentButton.isEnabled = false
        addCurrentButton.text = getString(R.string.ssid_unknown_network)
      }
    }
  }

  private fun onAddCurrentOrFixSsid() {
    val ssid = (application as WirePilotApp).container.ssidReader.snapshot().wifiSsids.firstOrNull()
    if (ssid != null) {
      controller.addExcludedSsid(ssid, tunnelName)
      refreshUi()
      return
    }
    when (ssidBlockerWhenUnreadable()) {
      SsidBlocker.NEARBY_WIFI_PERMISSION ->
        permissionLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
      SsidBlocker.FINE_LOCATION_PERMISSION ->
        permissionLauncher.launch(
          arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
      SsidBlocker.LOCATION_OFF ->
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
      SsidBlocker.UNKNOWN_NETWORK -> Unit
    }
  }

  private fun ssidBlockerWhenUnreadable(): SsidBlocker {
    return SsidReadinessEvaluator.blockerWhenUnreadable(
      SsidReadiness(
        nearbyWifiGranted = AppPermissions.nearbyWifiGranted(this),
        fineLocationGranted = AppPermissions.fineLocationGranted(this),
        locationEnabled = AppPermissions.locationEnabled(this),
      ),
    )
  }

  private fun showAddSsidDialog() {
    val input = EditText(this).apply {
      hint = getString(R.string.ssid_hint)
      setSingleLine()
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.add_ssid_title)
      .setView(input)
      .setPositiveButton(R.string.add) { _, _ ->
        controller.addExcludedSsid(input.text.toString(), tunnelName)
        refreshUi()
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  companion object {
    const val EXTRA_TUNNEL_NAME = "tunnel_name"

    fun intent(context: Context, tunnelName: String): Intent {
      return Intent(context, TunnelNetworksActivity::class.java).putExtra(EXTRA_TUNNEL_NAME, tunnelName)
    }
  }
}
