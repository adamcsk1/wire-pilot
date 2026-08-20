package com.wirepilot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.ui.SystemBarInsets

class LogActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var loggingSwitch: MaterialSwitch
  private lateinit var logPreview: TextView
  private lateinit var copyLogButton: MaterialButton
  private lateinit var clearLogButton: MaterialButton
  private var suppressLoggingSwitch = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_log)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    loggingSwitch = findViewById(R.id.loggingSwitch)
    logPreview = findViewById(R.id.logPreview)
    copyLogButton = findViewById(R.id.copyLogButton)
    clearLogButton = findViewById(R.id.clearLogButton)
    findViewById<MaterialToolbar>(R.id.logToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
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
    refreshUi()
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  private fun refreshUi() {
    val state = controller.viewState()
    suppressLoggingSwitch = true
    loggingSwitch.isChecked = state.loggingEnabled
    suppressLoggingSwitch = false
    logPreview.text = state.logCopyText.ifBlank { getString(R.string.log_empty) }
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
}
