package com.wirepilot.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.ControlSelection
import com.wirepilot.app.control.DurationFormatter
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.HomeViewState
import com.wirepilot.app.control.PauseOption
import com.wirepilot.app.control.SetupEvaluator
import com.wirepilot.app.control.SetupFlags
import com.wirepilot.app.control.SetupStep
import com.wirepilot.app.control.SsidBlocker
import com.wirepilot.app.control.SsidReadiness
import com.wirepilot.app.control.SsidReadinessEvaluator
import com.wirepilot.app.control.SkipReason
import com.wirepilot.app.control.StatusPresentation
import com.wirepilot.app.control.SystemSettingsTarget
import com.wirepilot.app.control.WireGuardContract
import com.wirepilot.app.platform.SettingsNavigator

class MainActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
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
  private lateinit var setupCard: MaterialCardView
  private lateinit var setupList: LinearLayout
  private lateinit var openWireGuardButton: MaterialButton
  private lateinit var appInfoButton: MaterialButton
  private lateinit var batteryOptimizationButton: MaterialButton
  private lateinit var unusedAppsButton: MaterialButton
  private lateinit var locationSettingsButton: MaterialButton
  private lateinit var loggingSwitch: MaterialSwitch
  private lateinit var settingsNavigator: SettingsNavigator
  private lateinit var logPreview: TextView
  private lateinit var copyLogButton: MaterialButton
  private lateinit var clearLogButton: MaterialButton
  private var suppressLoggingSwitch = false
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
    controller = (application as WirePilotApp).container.homeController
    settingsNavigator = SettingsNavigator(this)
    bindViews()
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

  private fun bindViews() {
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
    setupCard = findViewById(R.id.setupCard)
    setupList = findViewById(R.id.setupList)
    openWireGuardButton = findViewById(R.id.openWireGuardButton)
    appInfoButton = findViewById(R.id.appInfoButton)
    batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)
    unusedAppsButton = findViewById(R.id.unusedAppsButton)
    locationSettingsButton = findViewById(R.id.locationSettingsButton)
    loggingSwitch = findViewById(R.id.loggingSwitch)
    logPreview = findViewById(R.id.logPreview)
    copyLogButton = findViewById(R.id.copyLogButton)
    clearLogButton = findViewById(R.id.clearLogButton)
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
    openWireGuardButton.setOnClickListener { openWireGuardOrStore() }
    appInfoButton.setOnClickListener { openSystemSettings(SystemSettingsTarget.APP_INFO) }
    batteryOptimizationButton.setOnClickListener { openSystemSettings(SystemSettingsTarget.BATTERY_OPTIMIZATION) }
    unusedAppsButton.setOnClickListener { openSystemSettings(SystemSettingsTarget.UNUSED_APPS) }
    locationSettingsButton.setOnClickListener { openSystemSettings(SystemSettingsTarget.LOCATION) }
    applyNowButton.setOnClickListener {
      controller.applyNow()
      refreshUi()
    }
    loggingSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressLoggingSwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setLoggingEnabled(checked)
      refreshUi()
    }
    copyLogButton.setOnClickListener { copyLog() }
    clearLogButton.setOnClickListener {
      controller.clearLogs()
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
    bindSetup()
    bindLog(state)
    bindSsidAction(state)
  }

  private fun bindStatus(status: StatusPresentation) {
    when (status) {
      StatusPresentation.Watching -> {
        statusTitle.setText(R.string.status_watching)
        statusDetail.setText(
          if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
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

  private fun bindLog(state: HomeViewState) {
    suppressLoggingSwitch = true
    loggingSwitch.isChecked = state.loggingEnabled
    suppressLoggingSwitch = false
    logPreview.text = state.logPreview.ifBlank { getString(R.string.log_empty) }
    copyLogButton.isEnabled = state.logCopyText.isNotBlank()
    clearLogButton.isEnabled = state.logCopyText.isNotBlank()
  }

  private fun copyLog() {
    val text = controller.viewState().logCopyText
    if (text.isBlank()) {
      return
    }
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Wire Pilot log", text))
    Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
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

  private fun bindSetup() {
    val flags = SetupFlags(
      wireGuardInstalled = isWireGuardInstalled(),
      controlPermissionGranted = hasPermission(WireGuardContract.PERMISSION),
      nearbyWifiGranted = hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES),
      fineLocationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
      locationEnabled = isLocationEnabled(),
      notificationsGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
      tunnelNameSet = controller.viewState().tunnelName.isNotBlank(),
    )
    val steps = SetupEvaluator.steps(flags)
    setupList.removeAllViews()
    setupCard.isVisible = steps.isNotEmpty()
    steps.forEach { step ->
      val row = TextView(this).apply {
        text = setupLabel(step)
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface))
        textSize = 15f
        setPadding(0, 8, 0, 8)
      }
      setupList.addView(row)
    }
    openWireGuardButton.isVisible = true
  }

  private fun setupLabel(step: SetupStep): String {
    return when (step) {
      SetupStep.INSTALL_WIREGUARD -> getString(R.string.setup_install_wireguard)
      SetupStep.GRANT_CONTROL -> getString(R.string.setup_grant_control)
      SetupStep.ENABLE_REMOTE_CONTROL -> getString(R.string.setup_enable_remote_control)
      SetupStep.GRANT_NEARBY_WIFI -> getString(R.string.setup_grant_nearby_wifi)
      SetupStep.GRANT_FINE_LOCATION -> getString(R.string.setup_grant_fine_location)
      SetupStep.ENABLE_LOCATION -> getString(R.string.setup_enable_location)
      SetupStep.GRANT_NOTIFICATIONS -> getString(R.string.setup_grant_notifications)
      SetupStep.SET_TUNNEL_NAME -> getString(R.string.setup_set_tunnel_name)
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
      nearbyWifiGranted = hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES),
      fineLocationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
      locationEnabled = isLocationEnabled(),
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

  private fun openSystemSettings(target: SystemSettingsTarget) {
    if (!settingsNavigator.open(target)) {
      Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
  }

  private fun isLocationEnabled(): Boolean {
    val locationManager = getSystemService(LocationManager::class.java)
    return locationManager.isLocationEnabled
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
      hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    (application as WirePilotApp).container.watching.sync(watching)
  }

  private fun requestMissingPermissions() {
    val missing = buildList {
      if (!hasPermission(WireGuardContract.PERMISSION) && isWireGuardInstalled()) {
        add(WireGuardContract.PERMISSION)
      }
      if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
      }
      if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
      }
      if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    if (missing.isNotEmpty()) {
      permissionLauncher.launch(missing.toTypedArray())
    }
  }

  private fun openWireGuardOrStore() {
    if (isWireGuardInstalled()) {
      val launchIntent = packageManager.getLaunchIntentForPackage(WireGuardContract.PACKAGE_NAME)
      if (launchIntent != null) {
        startActivity(launchIntent)
        return
      }
    }
    val market = "market://details?id=${WireGuardContract.PACKAGE_NAME}".toUri()
    val play = Uri.parse("https://play.google.com/store/apps/details?id=${WireGuardContract.PACKAGE_NAME}")
    val intent = Intent(Intent.ACTION_VIEW, market)
    if (intent.resolveActivity(packageManager) != null) {
      startActivity(intent)
    } else {
      startActivity(Intent(Intent.ACTION_VIEW, play))
    }
  }

  private fun currentReadableSsid(): String? {
    val snapshot = (application as WirePilotApp).container.ssidReader.snapshot()
    return snapshot.wifiSsids.firstOrNull()
  }

  private fun isWireGuardInstalled(): Boolean {
    return try {
      packageManager.getPackageInfo(
        WireGuardContract.PACKAGE_NAME,
        PackageManager.PackageInfoFlags.of(0),
      )
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  private fun hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
  }
}
