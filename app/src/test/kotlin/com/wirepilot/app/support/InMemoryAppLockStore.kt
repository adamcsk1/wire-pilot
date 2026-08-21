package com.wirepilot.app.support

import com.wirepilot.app.data.AppLockState
import com.wirepilot.app.data.AppLockStore

class InMemoryAppLockStore(
  initial: AppLockState = AppLockState(),
) : AppLockStore {
  private var value: AppLockState = initial

  override fun read(): AppLockState = value

  override fun write(state: AppLockState) {
    value = state
  }
}
