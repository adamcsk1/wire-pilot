package com.wirepilot.app

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.AppLockSession
import com.wirepilot.app.data.ThemeMode
import com.wirepilot.app.control.SettingsRowStatus
import com.wirepilot.app.control.SettingsRowStatusPresenter
import com.wirepilot.app.control.SystemSettingsTarget
import com.wirepilot.app.data.ThemeModeStore
import com.wirepilot.app.platform.AppCompatThemeMode
import com.wirepilot.app.platform.BiometricAvailability
import com.wirepilot.app.platform.SettingsNavigator
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SystemBarInsets

class SettingsActivity : AppCompatActivity() {
  private lateinit var appLockSession: AppLockSession
  private lateinit var settingsNavigator: SettingsNavigator
  private lateinit var themeModes: ThemeModeStore
  private lateinit var themeModeGroup: RadioGroup
  private lateinit var appLockSwitch: MaterialSwitch
  private lateinit var biometricSwitch: MaterialSwitch
  private lateinit var locationPermissionStatus: TextView
  private lateinit var nearbyWifiStatus: TextView
  private lateinit var locationSettingsStatus: TextView
  private lateinit var batteryOptimizationStatus: TextView
  private lateinit var unusedAppsStatusView: TextView
  private var unusedAppsStatus = SettingsRowStatus.UNKNOWN
  private var suppressLockSwitch = false
  private var suppressBiometricSwitch = false
  private var suppressThemeSelection = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    val container = (application as WirePilotApp).container
    appLockSession = container.appLockSession
    themeModes = container.themeModes
    settingsNavigator = SettingsNavigator(this)
    themeModeGroup = findViewById(R.id.themeModeGroup)
    appLockSwitch = findViewById(R.id.appLockSwitch)
    biometricSwitch = findViewById(R.id.biometricSwitch)
    locationPermissionStatus = findViewById(R.id.locationPermissionStatus)
    nearbyWifiStatus = findViewById(R.id.nearbyWifiStatus)
    locationSettingsStatus = findViewById(R.id.locationSettingsStatus)
    batteryOptimizationStatus = findViewById(R.id.batteryOptimizationStatus)
    unusedAppsStatusView = findViewById(R.id.unusedAppsStatus)
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
    bindOnOff(locationPermissionStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.fineLocationGranted(this)))
    bindOnOff(nearbyWifiStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.nearbyWifiGranted(this)))
    bindOnOff(locationSettingsStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.locationEnabled(this)))
    bindBattery(batteryOptimizationStatus, SettingsRowStatusPresenter.fromFlag(AppPermissions.batteryUnrestricted(this)))
    bindUnused(unusedAppsStatusView, unusedAppsStatus)
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
}
