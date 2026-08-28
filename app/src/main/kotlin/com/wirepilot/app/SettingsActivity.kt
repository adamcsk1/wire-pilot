package com.wirepilot.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.AppLockSession
import com.wirepilot.app.control.UpdateCheckDecision
import com.wirepilot.app.data.ThemeMode
import com.wirepilot.app.control.SettingsRowStatus
import com.wirepilot.app.control.SettingsRowStatusPresenter
import com.wirepilot.app.control.SystemSettingsTarget
import com.wirepilot.app.data.ThemeModeStore
import com.wirepilot.app.data.UpdateCheckStore
import com.wirepilot.app.platform.AppCompatThemeMode
import com.wirepilot.app.platform.BiometricAvailability
import com.wirepilot.app.platform.SettingsNavigator
import com.wirepilot.app.platform.UpdateCheckRunner
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SystemBarInsets

class SettingsActivity : AppCompatActivity() {
  private lateinit var appLockSession: AppLockSession
  private lateinit var settingsNavigator: SettingsNavigator
  private lateinit var themeModes: ThemeModeStore
  private lateinit var updateChecks: UpdateCheckStore
  private lateinit var updateCheckRunner: UpdateCheckRunner
  private lateinit var themeModeGroup: RadioGroup
  private lateinit var appLockSwitch: MaterialSwitch
  private lateinit var biometricSwitch: MaterialSwitch
  private lateinit var updateNotifySwitch: MaterialSwitch
  private lateinit var checkUpdatesButton: MaterialButton
  private lateinit var locationPermissionStatus: TextView
  private lateinit var nearbyWifiStatus: TextView
  private lateinit var locationSettingsStatus: TextView
  private lateinit var batteryOptimizationStatus: TextView
  private lateinit var unusedAppsStatusView: TextView
  private lateinit var vpnPermissionStatus: TextView
  private var unusedAppsStatus = SettingsRowStatus.UNKNOWN
  private var suppressLockSwitch = false
  private var suppressBiometricSwitch = false
  private var suppressThemeSelection = false
  private var suppressUpdateNotifySwitch = false
  private val notifyPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    onNotifyPermissionResult(granted)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    val container = (application as WirePilotApp).container
    appLockSession = container.appLockSession
    themeModes = container.themeModes
    updateChecks = container.updateChecks
    updateCheckRunner = container.updateCheckRunner
    settingsNavigator = SettingsNavigator(this)
    themeModeGroup = findViewById(R.id.themeModeGroup)
    appLockSwitch = findViewById(R.id.appLockSwitch)
    biometricSwitch = findViewById(R.id.biometricSwitch)
    updateNotifySwitch = findViewById(R.id.updateNotifySwitch)
    checkUpdatesButton = findViewById(R.id.checkUpdatesButton)
    locationPermissionStatus = findViewById(R.id.locationPermissionStatus)
    nearbyWifiStatus = findViewById(R.id.nearbyWifiStatus)
    locationSettingsStatus = findViewById(R.id.locationSettingsStatus)
    batteryOptimizationStatus = findViewById(R.id.batteryOptimizationStatus)
    unusedAppsStatusView = findViewById(R.id.unusedAppsStatus)
    vpnPermissionStatus = findViewById(R.id.vpnPermissionStatus)
    findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    findViewById<MaterialButton>(R.id.appInfoButton).setOnClickListener {
      openSystemSettings(SystemSettingsTarget.APP_INFO)
    }
    findViewById<MaterialButton>(R.id.batteryOptimizationButton).setOnClickListener {
      openSystemSettings(SystemSettingsTarget.BATTERY_OPTIMIZATION)
    }
    findViewById<MaterialButton>(R.id.unusedAppsButton).setOnClickListener {
      openSystemSettings(SystemSettingsTarget.UNUSED_APPS)
    }
    findViewById<MaterialButton>(R.id.locationSettingsButton).setOnClickListener {
      openSystemSettings(SystemSettingsTarget.LOCATION)
    }
    findViewById<MaterialButton>(R.id.vpnSettingsButton).setOnClickListener {
      openSystemSettings(SystemSettingsTarget.VPN)
    }
    findViewById<MaterialButton>(R.id.githubLink).setOnClickListener { openGitHub() }
    checkUpdatesButton.setOnClickListener { checkForUpdates() }
    updateNotifySwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressUpdateNotifySwitch) {
        return@setOnCheckedChangeListener
      }
      setNotifyEnabledFromSwitch(checked)
    }
    findViewById<TextView>(R.id.appVersion).text = getString(R.string.settings_version, appVersionName())
    appLockSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressLockSwitch) {
        return@setOnCheckedChangeListener
      }
      if (checked) {
        startActivity(LockActivity.intent(this, LockActivity.MODE_SET))
      } else {
        startActivity(LockActivity.intent(this, LockActivity.MODE_DISABLE))
      }
    }
    biometricSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressBiometricSwitch) {
        return@setOnCheckedChangeListener
      }
      appLockSession.setBiometric(checked)
      refreshUi()
    }
    themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
      if (suppressThemeSelection) {
        return@setOnCheckedChangeListener
      }
      val mode = themeModeFor(checkedId) ?: return@setOnCheckedChangeListener
      themeModes.write(mode)
      AppCompatThemeMode.apply(mode)
    }
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
    refreshUnusedAppsStatus()
    refreshUi()
  }

  private fun refreshUi() {
    val lockEnabled = appLockSession.isEnabled()
    val biometricAvailable = BiometricAvailability.canAuthenticate(this)
    suppressLockSwitch = true
    appLockSwitch.isChecked = lockEnabled
    suppressLockSwitch = false
    biometricSwitch.isVisible = lockEnabled && biometricAvailable
    suppressBiometricSwitch = true
    biometricSwitch.isChecked = lockEnabled && appLockSession.state().biometricEnabled
    suppressBiometricSwitch = false
    suppressThemeSelection = true
    themeModeGroup.check(themeButtonId(themeModes.read()))
    suppressThemeSelection = false
    suppressUpdateNotifySwitch = true
    updateNotifySwitch.isChecked = updateChecks.read().notifyEnabled
    suppressUpdateNotifySwitch = false
    bindOnOff(locationPermissionStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.fineLocationGranted(this)))
    bindOnOff(nearbyWifiStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.nearbyWifiGranted(this)))
    bindOnOff(locationSettingsStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.locationEnabled(this)))
    bindBattery(batteryOptimizationStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.batteryUnrestricted(this)))
    bindUnused(unusedAppsStatusView, unusedAppsStatus)
    bindOnOff(vpnPermissionStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.vpnConsentGranted(this)))
  }

  private fun refreshUnusedAppsStatus() {
    val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(this)
    future.addListener(
      {
        val value = runCatching { future.get() }.getOrNull()
        unusedAppsStatus = when (value) {
          UnusedAppRestrictionsConstants.DISABLED -> SettingsRowStatus.ON
          UnusedAppRestrictionsConstants.API_30,
          UnusedAppRestrictionsConstants.API_30_BACKPORT,
          UnusedAppRestrictionsConstants.API_31,
          -> SettingsRowStatus.OFF
          else -> SettingsRowStatus.UNKNOWN
        }
        if (!isDestroyed) {
          bindUnused(unusedAppsStatusView, unusedAppsStatus)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun bindOnOff(view: TextView, status: SettingsRowStatus) {
    bindStatus(view, status, R.string.settings_status_on, R.string.settings_status_off)
  }

  private fun bindBattery(view: TextView, status: SettingsRowStatus) {
    bindStatus(view, status, R.string.settings_status_unrestricted, R.string.settings_status_optimized)
  }

  private fun bindUnused(view: TextView, status: SettingsRowStatus) {
    bindStatus(view, status, R.string.settings_status_allowed, R.string.settings_status_restricted)
  }

  private fun bindStatus(view: TextView, status: SettingsRowStatus, onText: Int, offText: Int) {
    view.setText(
      when (status) {
        SettingsRowStatus.ON -> onText
        SettingsRowStatus.OFF -> offText
        SettingsRowStatus.UNKNOWN -> R.string.settings_status_unknown
      },
    )
    view.setBackgroundResource(
      when (status) {
        SettingsRowStatus.ON -> R.drawable.status_on_background
        SettingsRowStatus.OFF -> R.drawable.status_off_background
        SettingsRowStatus.UNKNOWN -> R.drawable.status_unknown_background
      },
    )
    view.setTextColor(
      ContextCompat.getColor(
        this,
        when (status) {
          SettingsRowStatus.ON -> R.color.on_secondary_container
          SettingsRowStatus.OFF -> R.color.on_primary_container
          SettingsRowStatus.UNKNOWN -> R.color.on_surface_muted
        },
      ),
    )
  }

  private fun themeModeFor(checkedId: Int): ThemeMode? {
    return when (checkedId) {
      R.id.themeSystem -> ThemeMode.SYSTEM
      R.id.themeLight -> ThemeMode.LIGHT
      R.id.themeDark -> ThemeMode.DARK
      else -> null
    }
  }

  private fun themeButtonId(mode: ThemeMode): Int {
    return when (mode) {
      ThemeMode.SYSTEM -> R.id.themeSystem
      ThemeMode.LIGHT -> R.id.themeLight
      ThemeMode.DARK -> R.id.themeDark
    }
  }

  private fun openSystemSettings(target: SystemSettingsTarget) {
    if (!settingsNavigator.open(target)) {
      Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
  }

  private fun setNotifyEnabledFromSwitch(enabled: Boolean) {
    if (!enabled) {
      updateCheckRunner.setNotifyEnabled(false)
      return
    }
    if (AppPermissions.granted(this, Manifest.permission.POST_NOTIFICATIONS)) {
      updateCheckRunner.setNotifyEnabled(true)
      return
    }
    notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun onNotifyPermissionResult(granted: Boolean) {
    if (granted) {
      updateCheckRunner.setNotifyEnabled(true)
    }
    if (!isDestroyed) {
      refreshUi()
    }
  }

  private fun checkForUpdates() {
    checkUpdatesButton.isEnabled = false
    val mainExecutor = ContextCompat.getMainExecutor(this)
    updateCheckRunner.checkNow { decision ->
      mainExecutor.execute {
        if (isDestroyed) {
          return@execute
        }
        checkUpdatesButton.isEnabled = true
        Toast.makeText(this, messageFor(decision), Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun messageFor(decision: UpdateCheckDecision): String {
    return when (decision) {
      UpdateCheckDecision.UpToDate -> getString(R.string.settings_update_up_to_date)
      UpdateCheckDecision.NoRelease -> getString(R.string.settings_update_none)
      UpdateCheckDecision.Failed -> getString(R.string.settings_update_failed)
      UpdateCheckDecision.AlreadyNotified -> {
        val tag = updateChecks.read().lastNotifiedTag
        getString(R.string.settings_update_available, tag)
      }
      is UpdateCheckDecision.Available -> getString(R.string.settings_update_available, decision.tagName)
    }
  }

  private fun openGitHub() {
    val intent = Intent(Intent.ACTION_VIEW, getString(R.string.github_project_url).toUri())
    try {
      startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
  }

  private fun appVersionName(): String {
    val info = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    return info.versionName.orEmpty()
  }
}
