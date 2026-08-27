package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeftoverPlaintextConfTest {
  @Test
  fun encryptsOnlyParseablePlaintextWhenDecryptFails() {
    assertTrue(LeftoverPlaintextConf.shouldEncrypt(encryptedReadable = false, plaintextParses = true))
    assertFalse(LeftoverPlaintextConf.shouldEncrypt(encryptedReadable = true, plaintextParses = true))
    assertFalse(LeftoverPlaintextConf.shouldEncrypt(encryptedReadable = false, plaintextParses = false))
    assertFalse(LeftoverPlaintextConf.shouldEncrypt(encryptedReadable = true, plaintextParses = false))
  }
}
