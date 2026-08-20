package com.wirepilot.app.support

import com.wirepilot.app.data.ControlStore
import com.wirepilot.app.data.StoredControl

class InMemoryControlStore(
  initial: StoredControl = StoredControl(),
) : ControlStore {
  private var value: StoredControl = initial

  override fun read(): StoredControl = value

  override fun write(control: StoredControl) {
    value = control
  }
}
