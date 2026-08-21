package com.wirepilot.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.LogChannel
import com.wirepilot.app.ui.SystemBarInsets

class LogActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var policyLogStatus: TextView
  private lateinit var vpnLogStatus: TextView
  private lateinit var usageStatus: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_log)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    policyLogStatus = findViewById(R.id.policyLogStatus)
    vpnLogStatus = findViewById(R.id.vpnLogStatus)
    usageStatus = findViewById(R.id.usageStatus)
    findViewById<MaterialToolbar>(R.id.logToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    findViewById<MaterialCardView>(R.id.policyLogCard).setOnClickListener {
      startActivity(LogDetailActivity.intent(this, LogChannel.POLICY))
    }
    findViewById<MaterialCardView>(R.id.vpnLogCard).setOnClickListener {
      startActivity(LogDetailActivity.intent(this, LogChannel.VPN))
    }
    findViewById<MaterialCardView>(R.id.usageCard).setOnClickListener {
      startActivity(UsageActivity.intent(this))
    }
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  private fun refreshUi() {
    val state = controller.viewState()
    policyLogStatus.setText(statusRes(state.policyLoggingEnabled))
    vpnLogStatus.setText(statusRes(state.vpnLoggingEnabled))
    usageStatus.setText(statusRes(state.usageEnabled))
  }

  private fun statusRes(enabled: Boolean): Int {
    return if (enabled) R.string.log_status_on else R.string.log_status_off
  }
}
