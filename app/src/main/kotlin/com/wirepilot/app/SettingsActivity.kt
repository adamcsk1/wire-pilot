package com.wirepilot.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.AppLockSession
import com.wirepilot.app.control.SystemSettingsTarget
import com.wirepilot.app.platform.BiometricAvailability
import com.wirepilot.app.platform.SettingsNavigator
import com.wirepilot.app.ui.SystemBarInsets

class SettingsActivity : AppCompatActivity() {
  private lateinit var appLockSession: AppLockSession
  private lateinit var settingsNavigator: SettingsNavigator
  private lateinit var appLockSwitch: MaterialSwitch
  private lateinit var biometricSwitch: MaterialSwitch
  private var suppressLockSwitch = false
  private var suppressBiometricSwitch = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    val container = (application as WirePilotApp).container
    appLockSession = container.appLockSession
    settingsNavigator = SettingsNavigator(this)
    appLockSwitch = findViewById(R.id.appLockSwitch)
    biometricSwitch = findViewById(R.id.biometricSwitch)
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
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
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
  }

  private fun openSystemSettings(target: SystemSettingsTarget) {
    if (!settingsNavigator.open(target)) {
      Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
  }
}
