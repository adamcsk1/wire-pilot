package com.wirepilot.app

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.ControlSelection
import com.wirepilot.app.control.DurationFormatter
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.HomeViewState
import com.wirepilot.app.control.PauseOption
import com.wirepilot.app.control.SsidBlocker
import com.wirepilot.app.control.SsidReadiness
import com.wirepilot.app.control.SsidReadinessEvaluator
import com.wirepilot.app.control.SkipReason
import com.wirepilot.app.control.StatusPresentation
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SystemBarInsets

class MainActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var toolbar: MaterialToolbar
  private lateinit var statusTitle: TextView
  private lateinit var statusDetail: TextView
  private lateinit var activeTunnelLabel: TextView
  private lateinit var manageTunnelsButton: MaterialButton
  private lateinit var controlSwitch: MaterialSwitch
  private lateinit var pauseButton: MaterialButton
  private lateinit var vpnSwitch: MaterialSwitch
  private lateinit var applyNowButton: MaterialButton
  private lateinit var applyNowDetail: TextView
  private lateinit var manualVpnHint: TextView
  private var suppressControlSwitch = false
  private var suppressVpnSwitch = false
  private var pendingAfterVpnPrepare = "apply"
  private val onTunnelSettled = {
    if (!isDestroyed) {
      refreshUi()
    }
  }

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    startWatchingIfNeeded()
    refreshUi()
  }

  private val vpnPrepareLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) {
    if (VpnService.prepare(this) != null) {
      refreshUi()
      return@registerForActivityResult
    }
    if (pendingAfterVpnPrepare == "connect") {
      controller.connectManually()
    } else {
      controller.applyNow()
    }
    refreshUi()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    bindViews()
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayShowTitleEnabled(true)
    bindActions()
    requestMissingPermissions()
    startWatchingIfNeeded()
    refreshUi()
  }

  override fun onStart() {
    super.onStart()
    (application as WirePilotApp).container.tunnel.addSettledListener(onTunnelSettled)
  }

  override fun onResume() {
    super.onResume()
    startWatchingIfNeeded()
    refreshUi()
  }

  override fun onStop() {
    (application as WirePilotApp).container.tunnel.removeSettledListener(onTunnelSettled)
    super.onStop()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.home_toolbar, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_log -> {
        startActivity(Intent(this, LogActivity::class.java))
        true
      }
      R.id.action_settings -> {
        startActivity(Intent(this, SettingsActivity::class.java))
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun bindViews() {
    toolbar = findViewById(R.id.homeToolbar)
    statusTitle = findViewById(R.id.statusTitle)
    statusDetail = findViewById(R.id.statusDetail)
    activeTunnelLabel = findViewById(R.id.activeTunnelLabel)
    manageTunnelsButton = findViewById(R.id.manageTunnelsButton)
    controlSwitch = findViewById(R.id.controlSwitch)
    pauseButton = findViewById(R.id.pauseButton)
    vpnSwitch = findViewById(R.id.vpnSwitch)
    applyNowButton = findViewById(R.id.applyNowButton)
    applyNowDetail = findViewById(R.id.applyNowDetail)
    manualVpnHint = findViewById(R.id.manualVpnHint)
  }

  private fun bindActions() {
    manageTunnelsButton.setOnClickListener {
      startActivity(Intent(this, TunnelsActivity::class.java))
    }
    controlSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressControlSwitch) {
        return@setOnCheckedChangeListener
      }
      if (checked) {
        controller.enableControl()
        startWatchingIfNeeded()
        refreshUi()
        applyWithVpnConsent()
      } else {
        controller.disableControlForever()
        refreshUi()
      }
    }
    pauseButton.setOnClickListener { showPauseMenu() }
    applyNowButton.setOnClickListener { applyWithVpnConsent() }
    vpnSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressVpnSwitch) {
        return@setOnCheckedChangeListener
      }
      if (checked) {
        pendingAfterVpnPrepare = "connect"
        if (ensureVpnPrepared()) {
          controller.connectManually()
          refreshUi()
        }
      } else {
        controller.disconnectManually()
        refreshUi()
      }
    }
  }

  private fun refreshUi() {
    val state = controller.viewState()
    activeTunnelLabel.text = if (state.tunnelName.isBlank()) {
      getString(R.string.active_tunnel_none)
    } else {
      getString(R.string.active_tunnel, state.tunnelName)
    }
    bindStatus(state.status)
    bindControlSwitch(state)
    bindVpnSwitch(state)
    bindApplyNow(state)
  }

  private fun bindStatus(status: StatusPresentation) {
    when (status) {
      StatusPresentation.Watching -> {
        statusTitle.setText(R.string.status_watching)
        statusDetail.setText(
          if (AppPermissions.notificationsGranted(this)) {
            R.string.status_watching_detail
          } else {
            R.string.status_watching_no_notification
          },
        )
      }
      StatusPresentation.Disabled -> {
        statusTitle.setText(R.string.status_disabled)
        statusDetail.setText(R.string.status_disabled_detail)
      }
      is StatusPresentation.Paused -> {
        val remaining = DurationFormatter.remainingHoursAndMinutes(status.remainingMillis)
        statusTitle.setText(R.string.status_paused)
        statusDetail.text = getString(R.string.status_paused_detail, remaining.first, remaining.second)
      }
    }
  }

  private fun bindApplyNow(state: HomeViewState) {
    applyNowButton.isEnabled = state.applyNow.enabled
    applyNowButton.text = getString(R.string.apply_policy)
    val skipReason = state.applyNow.skipReason
    if (skipReason == null) {
      applyNowDetail.isVisible = false
    } else {
      applyNowDetail.isVisible = true
      applyNowDetail.text = when (skipReason) {
        SkipReason.CONTROL_DISABLED -> getString(R.string.apply_now_disabled)
        SkipReason.BLANK_TUNNEL_NAME -> getString(R.string.apply_now_blank_tunnel)
        SkipReason.WIFI_SSID_UNREADABLE -> ssidBlockerMessage(ssidBlockerWhenUnreadable())
        SkipReason.MOBILE_DISABLED -> getString(R.string.apply_now_mobile_disabled)
      }
    }
  }

  private fun bindControlSwitch(state: HomeViewState) {
    val hasTunnel = state.tunnelName.isNotBlank()
    val armed = state.controlSelection != ControlSelection.OFF
    controlSwitch.isEnabled = hasTunnel || armed
    suppressControlSwitch = true
    controlSwitch.isChecked = armed
    suppressControlSwitch = false
    pauseButton.isVisible = hasTunnel && armed
  }

  private fun bindVpnSwitch(state: HomeViewState) {
    val hasTunnel = state.tunnelName.isNotBlank()
    val manual = state.controlSelection != ControlSelection.ON
    suppressVpnSwitch = true
    vpnSwitch.isChecked = state.vpnConnected
    vpnSwitch.isEnabled = hasTunnel && manual
    suppressVpnSwitch = false
    manualVpnHint.isVisible = hasTunnel && manual
  }

  private fun ssidReadiness(): SsidReadiness {
    return SsidReadiness(
      nearbyWifiGranted = AppPermissions.nearbyWifiGranted(this),
      fineLocationGranted = AppPermissions.fineLocationGranted(this),
      locationEnabled = AppPermissions.locationEnabled(this),
    )
  }

  private fun ssidBlockerWhenUnreadable(): SsidBlocker {
    return SsidReadinessEvaluator.blockerWhenUnreadable(ssidReadiness())
  }

  private fun ssidBlockerMessage(blocker: SsidBlocker): String {
    return when (blocker) {
      SsidBlocker.NEARBY_WIFI_PERMISSION -> getString(R.string.apply_now_need_nearby)
      SsidBlocker.FINE_LOCATION_PERMISSION -> getString(R.string.apply_now_need_fine_location)
      SsidBlocker.LOCATION_OFF -> getString(R.string.apply_now_need_location)
      SsidBlocker.UNKNOWN_NETWORK -> getString(R.string.ssid_unknown_network)
    }
  }

  private fun showPauseMenu() {
    val paused = controller.viewState().controlSelection == ControlSelection.PAUSE
    val options = arrayOf(
      PauseOption.HOURS_1,
      PauseOption.HOURS_2,
      PauseOption.HOURS_4,
      PauseOption.HOURS_8,
      PauseOption.HOURS_12,
      PauseOption.HOURS_24,
    )
    val durationLabels = arrayOf(
      getString(R.string.pause_1h),
      getString(R.string.pause_2h),
      getString(R.string.pause_4h),
      getString(R.string.pause_8h),
      getString(R.string.pause_12h),
      getString(R.string.pause_24h),
    )
    val items = if (paused) {
      arrayOf(getString(R.string.pause_resume)) + durationLabels
    } else {
      durationLabels
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.pause_title)
      .setItems(items) { _, which ->
        if (paused && which == 0) {
          controller.enableControl()
          startWatchingIfNeeded()
          refreshUi()
          applyWithVpnConsent()
        } else {
          val optionIndex = if (paused) which - 1 else which
          controller.pauseFor(options[optionIndex])
          refreshUi()
        }
      }
      .show()
  }

  private fun startWatchingIfNeeded() {
    val state = controller.viewState()
    val watching = state.controlSelection == ControlSelection.ON &&
      state.tunnelName.isNotBlank() &&
      AppPermissions.notificationsGranted(this)
    (application as WirePilotApp).container.watching.sync(watching)
  }

  private fun requestMissingPermissions() {
    val missing = buildList {
      if (!AppPermissions.nearbyWifiGranted(this@MainActivity)) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
      }
      if (!AppPermissions.fineLocationGranted(this@MainActivity)) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
      }
      if (!AppPermissions.notificationsGranted(this@MainActivity)) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    if (missing.isNotEmpty()) {
      permissionLauncher.launch(missing.toTypedArray())
    }
  }

  private fun applyWithVpnConsent() {
    pendingAfterVpnPrepare = "apply"
    if (!ensureVpnPrepared()) {
      return
    }
    controller.applyNow()
    refreshUi()
  }

  private fun ensureVpnPrepared(): Boolean {
    val prepare = VpnService.prepare(this)
    if (prepare != null) {
      Toast.makeText(this, R.string.vpn_prepare_needed, Toast.LENGTH_SHORT).show()
      vpnPrepareLauncher.launch(prepare)
      return false
    }
    return true
  }
}
