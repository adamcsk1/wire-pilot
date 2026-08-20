package com.wirepilot.app

import android.net.VpnService
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.SetupEvaluator
import com.wirepilot.app.control.SetupFlags
import com.wirepilot.app.control.SystemSettingsTarget
import com.wirepilot.app.platform.SettingsNavigator
import com.wirepilot.app.ui.AppPermissions
import com.wirepilot.app.ui.SetupLabels
import com.wirepilot.app.ui.SystemBarInsets

class SettingsActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var settingsNavigator: SettingsNavigator
  private lateinit var setupCard: MaterialCardView
  private lateinit var setupList: LinearLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    settingsNavigator = SettingsNavigator(this)
    setupCard = findViewById(R.id.setupCard)
    setupList = findViewById(R.id.setupList)
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
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  private fun refreshUi() {
    val flags = SetupFlags(
      nearbyWifiGranted = AppPermissions.nearbyWifiGranted(this),
      fineLocationGranted = AppPermissions.fineLocationGranted(this),
      locationEnabled = AppPermissions.locationEnabled(this),
      notificationsGranted = AppPermissions.notificationsGranted(this),
      tunnelImported = controller.viewState().importedTunnels.isNotEmpty(),
      vpnPrepared = VpnService.prepare(this) == null,
    )
    val steps = SetupEvaluator.steps(flags)
    setupList.removeAllViews()
    setupCard.isVisible = steps.isNotEmpty()
    steps.forEach { step ->
      setupList.addView(
        TextView(this).apply {
          text = getString(SetupLabels.stringRes(step))
          setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_surface))
          textSize = 15f
          setPadding(0, 8, 0, 8)
        },
      )
    }
  }

  private fun openSystemSettings(target: SystemSettingsTarget) {
    if (!settingsNavigator.open(target)) {
      Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
  }
}
