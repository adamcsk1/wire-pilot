package com.wirepilot.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wirepilot.app.control.AppLockPolicy
import com.wirepilot.app.control.AppLockSession
import com.wirepilot.app.platform.BiometricAvailability
import com.wirepilot.app.ui.SystemBarInsets
import java.security.SecureRandom

class LockActivity : AppCompatActivity() {
  private lateinit var session: AppLockSession
  private lateinit var mode: String
  private lateinit var lockMessage: TextView
  private lateinit var pinInput: TextInputEditText
  private lateinit var confirmPinLayout: TextInputLayout
  private lateinit var confirmPinInput: TextInputEditText
  private lateinit var lockActionButton: MaterialButton
  private lateinit var biometricButton: MaterialButton

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    session = (application as WirePilotApp).container.appLockSession
    mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK
    if (mode == MODE_SET && session.isEnabled()) {
      finish()
      return
    }
    if (mode == MODE_DISABLE && !session.isEnabled()) {
      finish()
      return
    }
    if (mode == MODE_UNLOCK && !session.needsChallenge()) {
      finish()
      return
    }
    setContentView(R.layout.activity_lock)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    lockMessage = findViewById(R.id.lockMessage)
    pinInput = findViewById(R.id.pinInput)
    confirmPinLayout = findViewById(R.id.confirmPinLayout)
    confirmPinInput = findViewById(R.id.confirmPinInput)
    lockActionButton = findViewById(R.id.lockActionButton)
    biometricButton = findViewById(R.id.biometricButton)
    findViewById<MaterialToolbar>(R.id.lockToolbar).setTitle(titleRes())
    lockMessage.setText(messageRes())
    confirmPinLayout.isVisible = mode == MODE_SET
    lockActionButton.setText(actionRes())
    val showBiometric = mode == MODE_UNLOCK &&
      session.state().biometricEnabled &&
      BiometricAvailability.canAuthenticate(this)
    biometricButton.isVisible = showBiometric
    lockActionButton.setOnClickListener { submit() }
    biometricButton.setOnClickListener { promptBiometric() }
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (mode == MODE_UNLOCK) {
            moveTaskToBack(true)
          } else {
            finish()
          }
        }
      },
    )
    if (showBiometric) {
      promptBiometric()
    }
  }

  override fun onStop() {
    if (::pinInput.isInitialized) {
      pinInput.text = null
      confirmPinInput.text = null
    }
    super.onStop()
  }

  private fun submit() {
    val pin = pinInput.text?.toString().orEmpty()
    when (mode) {
      MODE_SET -> {
        val confirm = confirmPinInput.text?.toString().orEmpty()
        if (!AppLockPolicy.isValidPin(pin)) {
          Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show()
          return
        }
        if (pin != confirm) {
          Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
          return
        }
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        if (session.enable(pin, confirm, AppLockPolicy.saltHex(salt))) {
          finish()
        } else {
          Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT).show()
        }
      }
      MODE_DISABLE -> {
        if (session.disable(pin)) {
          finish()
        } else {
          Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
        }
      }
      else -> {
        if (session.verifyPin(pin)) {
          finish()
        } else {
          Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  private fun promptBiometric() {
    val authenticators = BiometricAvailability.authenticators(this)
    if (authenticators == 0) {
      return
    }
    val prompt = BiometricPrompt(
      this,
      ContextCompat.getMainExecutor(this),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          if (session.unlockWithBiometric()) {
            finish()
          }
        }
      },
    )
    prompt.authenticate(
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.lock_title))
        .setNegativeButtonText(getString(R.string.use_pin))
        .setAllowedAuthenticators(authenticators)
        .build(),
    )
  }

  private fun titleRes(): Int {
    return when (mode) {
      MODE_SET -> R.string.set_pin
      MODE_DISABLE -> R.string.turn_off_app_lock
      else -> R.string.lock_title
    }
  }

  private fun messageRes(): Int {
    return when (mode) {
      MODE_SET -> R.string.lock_set_message
      MODE_DISABLE -> R.string.lock_disable_message
      else -> R.string.lock_unlock_message
    }
  }

  private fun actionRes(): Int {
    return when (mode) {
      MODE_SET -> R.string.set_pin
      MODE_DISABLE -> R.string.turn_off_app_lock
      else -> R.string.unlock
    }
  }

  companion object {
    const val EXTRA_MODE = "mode"
    const val MODE_UNLOCK = "unlock"
    const val MODE_SET = "set"
    const val MODE_DISABLE = "disable"

    fun intent(context: Context, mode: String = MODE_UNLOCK): Intent {
      return Intent(context, LockActivity::class.java)
        .putExtra(EXTRA_MODE, mode)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
  }
}
