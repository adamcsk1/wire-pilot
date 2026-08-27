package com.wirepilot.app.data

data class StoredUpdateCheck(
  val notifyEnabled: Boolean = true,
  val lastCheckEpochMillis: Long = 0L,
  val lastNotifiedTag: String = "",
)

interface UpdateCheckStore {
  fun read(): StoredUpdateCheck
  fun write(value: StoredUpdateCheck)
}

object EmptyUpdateCheckStore : UpdateCheckStore {
  override fun read(): StoredUpdateCheck = StoredUpdateCheck()
  override fun write(value: StoredUpdateCheck) = Unit
}

object UpdateCheckCodec {
  fun encode(value: StoredUpdateCheck): String {
    val flag = if (value.notifyEnabled) "1" else "0"
    return "$flag\t${value.lastCheckEpochMillis}\t${sanitize(value.lastNotifiedTag)}"
  }

  fun decode(raw: String?): StoredUpdateCheck {
    if (raw.isNullOrEmpty()) {
      return StoredUpdateCheck()
    }
    val parts = raw.split('\t', limit = 3)
    val notifyEnabled = parts.getOrNull(0) != "0"
    val lastCheckEpochMillis = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val lastNotifiedTag = parts.getOrNull(2).orEmpty()
    return StoredUpdateCheck(
      notifyEnabled = notifyEnabled,
      lastCheckEpochMillis = lastCheckEpochMillis,
      lastNotifiedTag = lastNotifiedTag,
    )
  }

  private fun sanitize(value: String): String {
    return value.replace('\t', ' ').replace('\n', ' ')
  }
}
