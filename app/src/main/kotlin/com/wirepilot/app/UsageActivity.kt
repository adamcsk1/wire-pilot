package com.wirepilot.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.wirepilot.app.control.ByteFormatter
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.ui.SystemBarInsets

class UsageActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var usageSwitch: MaterialSwitch
  private lateinit var usageHint: TextView
  private lateinit var usageNumbers: LinearLayout
  private lateinit var usageRx: TextView
  private lateinit var usageTx: TextView
  private val handler = Handler(Looper.getMainLooper())
  private val refresh = object : Runnable {
    override fun run() {
      bindUsage()
      handler.postDelayed(this, REFRESH_INTERVAL_MS)
    }
  }
  private var suppressSwitch = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_usage)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    usageSwitch = findViewById(R.id.usageSwitch)
    usageHint = findViewById(R.id.usageHint)
    usageNumbers = findViewById(R.id.usageNumbers)
    usageRx = findViewById(R.id.usageRx)
    usageTx = findViewById(R.id.usageTx)
    findViewById<MaterialToolbar>(R.id.usageToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    usageSwitch.setOnCheckedChangeListener { _, checked ->
      if (suppressSwitch) {
        return@setOnCheckedChangeListener
      }
      controller.setUsageEnabled(checked)
      refreshUi()
    }
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  override fun onPause() {
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  private fun refreshUi() {
    handler.removeCallbacks(refresh)
    bindUsage()
    if (controller.viewState().usageEnabled) {
      handler.postDelayed(refresh, REFRESH_INTERVAL_MS)
    }
  }

  private fun bindUsage() {
    val snapshot = controller.usageSnapshot()
    suppressSwitch = true
    usageSwitch.isChecked = snapshot.enabled
    suppressSwitch = false
    if (!snapshot.enabled) {
      usageHint.text = getString(R.string.usage_disabled_hint)
      usageHint.isVisible = true
      usageNumbers.isVisible = false
      return
    }
    if (!snapshot.connected) {
      usageHint.text = getString(R.string.usage_vpn_down)
      usageHint.isVisible = true
      usageNumbers.isVisible = false
      return
    }
    usageHint.isVisible = false
    usageNumbers.isVisible = true
    usageRx.text = ByteFormatter.format(snapshot.rxBytes)
    usageTx.text = ByteFormatter.format(snapshot.txBytes)
  }

  companion object {
    private const val REFRESH_INTERVAL_MS = 1000L

    fun intent(context: Context): Intent {
      return Intent(context, UsageActivity::class.java)
    }
  }
}
