package com.wirepilot.app.data

interface ControlStore {
  fun read(): StoredControl
  fun write(control: StoredControl)
}
