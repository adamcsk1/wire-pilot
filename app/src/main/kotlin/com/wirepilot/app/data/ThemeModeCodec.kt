package com.wirepilot.app.data

object ThemeModeCodec {
  fun encode(mode: ThemeMode): String {
    return mode.name.lowercase()
  }

  fun decode(raw: String?): ThemeMode {
    return raw?.uppercase()?.let { name ->
      runCatching { ThemeMode.valueOf(name) }.getOrNull()
    } ?: ThemeMode.SYSTEM
  }
}
