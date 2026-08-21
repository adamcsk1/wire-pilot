package com.wirepilot.app.data

interface ThemeModeStore {
  fun read(): ThemeMode
  fun write(mode: ThemeMode)
}

object EmptyThemeModeStore : ThemeModeStore {
  override fun read(): ThemeMode = ThemeMode.SYSTEM
  override fun write(mode: ThemeMode) = Unit
}
