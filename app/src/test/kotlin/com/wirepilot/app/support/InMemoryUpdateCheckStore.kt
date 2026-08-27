package com.wirepilot.app.support

import com.wirepilot.app.data.StoredUpdateCheck
import com.wirepilot.app.data.UpdateCheckStore

class InMemoryUpdateCheckStore(
  initial: StoredUpdateCheck = StoredUpdateCheck(),
) : UpdateCheckStore {
  private var value: StoredUpdateCheck = initial

  override fun read(): StoredUpdateCheck = value

  override fun write(value: StoredUpdateCheck) {
    this.value = value
  }
}
