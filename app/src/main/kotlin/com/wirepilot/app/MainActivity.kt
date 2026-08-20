package com.wirepilot.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.wirepilot.app.control.WireGuardContract
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SystemBarInsets

class MainActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var toolbar: MaterialToolbar
  private lateinit var statusTitle: TextView
  private lateinit var statusDetail: TextView
  private lateinit var tunnelNameInput: EditText
  private lateinit var ssidList: LinearLayout
  private lateinit var emptySsids: TextView
  private lateinit var addCurrentButton: MaterialButton
  private lateinit var addSsidButton: MaterialButton
  private lateinit var connectOnMobileSwitch: MaterialSwitch
  private lateinit var enableButton: MaterialButton
  private lateinit var pauseButton: MaterialButton
  private lateinit var disableButton: MaterialButton
  private lateinit var applyNowButton: MaterialButton
  private lateinit var applyNowDetail: TextView
  private var suppressMobileSwitch = false

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    startWatchingIfNeeded()
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

  override fun onResume() {
    super.onResume()
    startWatchingIfNeeded()
    refreshUi()
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
    tunnelNameInput = findViewById(R.id.tunnelNameInput)
    ssidList = findViewById(R.id.ssidList)
    emptySsids = findViewById(R.id.emptySsids)
    addCurrentButton = findViewById(R.id.addCurrentButton)
    addSsidButton = findViewById(R.id.addSsidButton)
    connectOnMobileSwitch = findViewById(R.id.connectOnMobileSwitch)
    enableButton = findViewById(R.id.enableButton)
    pauseButton = findViewById(R.id.pauseButton)
    disableButton = findViewById(R.id.disableButton)
    applyNowButton = findViewById(R.id.applyNowButton)
    applyNowDetail = findViewById(R.id.applyNowDetail)
  }

  private fun bindActions() {
    tunnelNameInput.doAfterTextChanged { text ->
      controller.setTunnelName(text?.toString().orEmpty())
    }
    tunnelNameInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        refreshUi()
        true
      } else {
        false
      }
    }
    addCurrentButton.setOnClickListener { onAddCurrentOrFixSsid() }
    addSsidButton.setOnClickListener { showAddSsidDialog() }
    connectOnMobileSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressMobileSwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setConnectOnMobile(checked)
      refreshUi()
    }
    enableButton.setOnClickListener {
      controller.enableControl()
      startWatchingIfNeeded()
      refreshUi()
    }
    disableButton.setOnClickListener {
      controller.disableControlForever()
      refreshUi()
    }
    pauseButton.setOnClickListener { showPauseMenu() }
    applyNowButton.setOnClickListener {
      controller.applyNow()
      refreshUi()
    }
  }

  private fun refreshUi() {
    val state = controller.viewState()
    if (tunnelNameInput.text.toString() != state.tunnelName) {
      tunnelNameInput.setText(state.tunnelName)
      tunnelNameInput.setSelection(state.tunnelName.length)
    }
    bindStatus(state.status)
    bindControlButtons(state.controlSelection)
    bindConnectOnMobile(state.connectOnMobile)
    bindApplyNow(state)
    bindSsids(state.excludedSsids)
    bindSsidAction(state)
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

  private fun bindControlButtons(selection: ControlSelection) {
    styleControlButton(enableButton, selected = selection == ControlSelection.ON)
    styleControlButton(pauseButton, selected = selection == ControlSelection.PAUSE)
    styleControlButton(disableButton, selected = selection == ControlSelection.OFF)
  }

  private fun styleControlButton(button: MaterialButton, selected: Boolean) {
    button.isCheckable = true
    button.isChecked = selected
    button.isEnabled = !selected
  }

  private fun bindConnectOnMobile(enabled: Boolean) {
    suppressMobileSwitch = true
    connectOnMobileSwitch.isChecked = enabled
    suppressMobileSwitch = false
  }

  private fun bindSsids(ssids: List<String>) {
    ssidList.removeAllViews()
    emptySsids.isVisible = ssids.isEmpty()
    ssids.forEach { ssid ->
      val row = layoutInflater.inflate(R.layout.item_ssid, ssidList, false)
      row.findViewById<TextView>(R.id.ssidName).text = ssid
      row.findViewById<MaterialButton>(R.id.removeSsidButton).setOnClickListener {
        controller.removeExcludedSsid(ssid)
        refreshUi()
      }
      ssidList.addView(row)
    }
  }

  private fun bindSsidAction(state: HomeViewState) {
    val snapshot = (application as WirePilotApp).container.ssidReader.snapshot()
    val currentSsid = snapshot.wifiSsids.firstOrNull()
    if (currentSsid != null) {
      addCurrentButton.isEnabled = currentSsid !in state.excludedSsids
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
    val ssid = currentReadableSsid()
    if (ssid != null) {
      controller.addExcludedSsid(ssid)
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

  private fun showAddSsidDialog() {
    val input = EditText(this).apply {
      hint = getString(R.string.ssid_hint)
      setSingleLine()
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.add_ssid_title)
      .setView(input)
      .setPositiveButton(R.string.add) { _, _ ->
        controller.addExcludedSsid(input.text.toString())
        refreshUi()
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun showPauseMenu() {
    val items = arrayOf(
      getString(R.string.pause_1h),
      getString(R.string.pause_2h),
      getString(R.string.pause_4h),
      getString(R.string.pause_8h),
      getString(R.string.pause_12h),
      getString(R.string.pause_24h),
    )
    val options = arrayOf(
      PauseOption.HOURS_1,
      PauseOption.HOURS_2,
      PauseOption.HOURS_4,
      PauseOption.HOURS_8,
      PauseOption.HOURS_12,
      PauseOption.HOURS_24,
    )
    AlertDialog.Builder(this)
      .setTitle(R.string.pause_title)
      .setItems(items) { _, which ->
        controller.pauseFor(options[which])
        refreshUi()
      }
      .show()
  }

  private fun startWatchingIfNeeded() {
    val watching = controller.viewState().controlSelection == ControlSelection.ON &&
      AppPermissions.notificationsGranted(this)
    (application as WirePilotApp).container.watching.sync(watching)
  }

  private fun requestMissingPermissions() {
    val missing = buildList {
      if (!AppPermissions.controlGranted(this@MainActivity) &&
        AppPermissions.wireGuardInstalled(this@MainActivity)
      ) {
        add(WireGuardContract.PERMISSION)
      }
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

  private fun currentReadableSsid(): String? {
    return (application as WirePilotApp).container.ssidReader.snapshot().wifiSsids.firstOrNull()
  }
}
