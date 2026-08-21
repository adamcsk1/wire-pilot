package com.wirepilot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.LogChannel
import com.wirepilot.app.ui.SystemBarInsets

class LogDetailActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var channel: LogChannel
  private lateinit var loggingSwitch: MaterialSwitch
  private lateinit var logPreview: TextView
  private lateinit var copyLogButton: MaterialButton
  private lateinit var clearLogButton: MaterialButton
  private var suppressSwitch = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val parsed = intent.getStringExtra(EXTRA_CHANNEL)?.let { name ->
      runCatching { LogChannel.valueOf(name) }.getOrNull()
    }
    if (parsed == null) {
      finish()
      return
    }
    channel = parsed
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.activity_log_detail)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    loggingSwitch = findViewById(R.id.loggingSwitch)
    logPreview = findViewById(R.id.logPreview)
    copyLogButton = findViewById(R.id.copyLogButton)
    clearLogButton = findViewById(R.id.clearLogButton)
    val toolbar = findViewById<MaterialToolbar>(R.id.logDetailToolbar)
    toolbar.setTitle(titleRes())
    toolbar.setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    loggingSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressSwitch) {
        return@setOnCheckedChangeListener
      }
      setEnabled(checked)
      refreshUi()
    }
    copyLogButton.setOnClickListener { copy(logText()) }
    clearLogButton.setOnClickListener {
      clear()
      refreshUi()
    }
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
    if (::controller.isInitialized) {
      refreshUi()
    }
  }

  private fun refreshUi() {
    val state = controller.viewState()
    val enabled = if (channel == LogChannel.POLICY) {
      state.policyLoggingEnabled
    } else {
      state.vpnLoggingEnabled
    }
    suppressSwitch = true
    loggingSwitch.isChecked = enabled
    suppressSwitch = false
    val text = logText()
    logPreview.text = text.ifBlank { getString(R.string.log_empty) }
    copyLogButton.isEnabled = text.isNotBlank()
    clearLogButton.isEnabled = text.isNotBlank()
  }

  private fun logText(): String {
    val state = controller.viewState()
    return if (channel == LogChannel.POLICY) {
      state.policyLogText
    } else {
      state.vpnLogText
    }
  }

  private fun setEnabled(enabled: Boolean) {
    if (channel == LogChannel.POLICY) {
      controller.setPolicyLoggingEnabled(enabled)
    } else {
      controller.setVpnLoggingEnabled(enabled)
    }
  }

  private fun clear() {
    if (channel == LogChannel.POLICY) {
      controller.clearPolicyLogs()
    } else {
      controller.clearVpnLogs()
    }
  }

  private fun titleRes(): Int {
    return if (channel == LogChannel.POLICY) {
      R.string.policy_logging
    } else {
      R.string.vpn_logging
    }
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

  companion object {
    const val EXTRA_CHANNEL = "channel"

    fun intent(context: Context, channel: LogChannel): Intent {
      return Intent(context, LogDetailActivity::class.java)
        .putExtra(EXTRA_CHANNEL, channel.name)
    }
  }
}
