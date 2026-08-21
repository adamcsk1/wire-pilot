package com.wirepilot.app.data

interface AppLockStore {
  fun read(): AppLockState
  fun write(state: AppLockState)
}

object EmptyAppLockStore : AppLockStore {
  override fun read(): AppLockState = AppLockState()
  override fun write(state: AppLockState) = Unit
}
