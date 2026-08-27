package com.wirepilot.app.control

object LeftoverPlaintextConf {
  fun shouldEncrypt(encryptedReadable: Boolean, plaintextParses: Boolean): Boolean {
    return !encryptedReadable && plaintextParses
  }
}
