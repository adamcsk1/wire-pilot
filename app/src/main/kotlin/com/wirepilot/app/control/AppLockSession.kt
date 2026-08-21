package com.wirepilot.app.control

import com.wirepilot.app.data.AppLockState
import com.wirepilot.app.data.AppLockStore

class AppLockSession(
  private val store: AppLockStore,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  private var unlocked = false

  fun state(): AppLockState {
    return store.read()
  }

  fun isEnabled(): Boolean {
    return store.read().enabled
  }

  fun needsChallenge(): Boolean {
    return isEnabled() && !unlocked
  }

  fun lock() {
    unlocked = false
  }

  fun unlock() {
    unlocked = true
  }

  fun lockoutRemainingMillis(): Long {
    return AppLockPolicy.remainingLockMillis(store.read(), clock())
  }

  fun verifyPin(pin: String): Boolean {
    val checked = AppLockPolicy.checkPin(store.read(), pin, clock())
    store.write(checked.state)
    if (checked.accepted) {
      unlocked = true
    }
    return checked.accepted
  }

  fun unlockWithBiometric(): Boolean {
    if (!store.read().biometricEnabled) {
      return false
    }
    unlocked = true
    return true
  }

  fun enable(pin: String, confirmPin: String, saltHex: String): Boolean {
    if (store.read().enabled) {
      return false
    }
    val next = AppLockPolicy.enable(pin, confirmPin, saltHex) ?: return false
    store.write(next)
    unlocked = true
    return true
  }

  fun disable(pin: String): Boolean {
    val checked = AppLockPolicy.checkPin(store.read(), pin, clock())
    if (!checked.accepted) {
      store.write(checked.state)
      return false
    }
    store.write(AppLockState())
    unlocked = true
    return true
  }

  fun setBiometric(enabled: Boolean): Boolean {
    val current = store.read()
    if (!current.enabled) {
      return false
    }
    store.write(AppLockPolicy.setBiometric(current, enabled))
    return true
  }
}
