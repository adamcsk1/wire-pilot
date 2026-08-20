package com.wirepilot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.ui.SystemBarInsets

class LogActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var policyLoggingSwitch: MaterialSwitch
  private lateinit var vpnLoggingSwitch: MaterialSwitch
  private lateinit var policyLogPreview: TextView
  private lateinit var vpnLogPreview: TextView
  private lateinit var copyPolicyLogButton: MaterialButton
  private lateinit var clearPolicyLogButton: MaterialButton
  private lateinit var copyVpnLogButton: MaterialButton
  private lateinit var clearVpnLogButton: MaterialButton
  private var suppressPolicySwitch = false
  private var suppressVpnSwitch = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_log)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    policyLoggingSwitch = findViewById(R.id.policyLoggingSwitch)
    vpnLoggingSwitch = findViewById(R.id.vpnLoggingSwitch)
    policyLogPreview = findViewById(R.id.policyLogPreview)
    vpnLogPreview = findViewById(R.id.vpnLogPreview)
    copyPolicyLogButton = findViewById(R.id.copyPolicyLogButton)
    clearPolicyLogButton = findViewById(R.id.clearPolicyLogButton)
    copyVpnLogButton = findViewById(R.id.copyVpnLogButton)
    clearVpnLogButton = findViewById(R.id.clearVpnLogButton)
    findViewById<MaterialToolbar>(R.id.logToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    policyLoggingSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressPolicySwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setPolicyLoggingEnabled(checked)
      refreshUi()
    }
    vpnLoggingSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressVpnSwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setVpnLoggingEnabled(checked)
      refreshUi()
    }
    copyPolicyLogButton.setOnClickListener { copy(controller.viewState().policyLogText) }
    copyVpnLogButton.setOnClickListener { copy(controller.viewState().vpnLogText) }
    clearPolicyLogButton.setOnClickListener {
      controller.clearPolicyLogs()
      refreshUi()
    }
    clearVpnLogButton.setOnClickListener {
      controller.clearVpnLogs()
      refreshUi()
    }
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  private fun refreshUi() {
    val state = controller.viewState()
    suppressPolicySwitch = true
    policyLoggingSwitch.isChecked = state.policyLoggingEnabled
    suppressPolicySwitch = false
    suppressVpnSwitch = true
    vpnLoggingSwitch.isChecked = state.vpnLoggingEnabled
    suppressVpnSwitch = false
    policyLogPreview.text = state.policyLogText.ifBlank { getString(R.string.log_empty) }
    vpnLogPreview.text = state.vpnLogText.ifBlank { getString(R.string.log_empty) }
    val policyOn = state.policyLogText.isNotBlank()
    val vpnOn = state.vpnLogText.isNotBlank()
    copyPolicyLogButton.isEnabled = policyOn
    clearPolicyLogButton.isEnabled = policyOn
    copyVpnLogButton.isEnabled = vpnOn
    clearVpnLogButton.isEnabled = vpnOn
  }

  private fun copy(text: String) {
    if (text.isBlank()) {
      return
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.copy_log)
      .setMessage(R.string.copy_log_contains_networks)
      .setPositiveButton(R.string.copy_log) { _, _ ->
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("WirePilot log", text))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }
}
