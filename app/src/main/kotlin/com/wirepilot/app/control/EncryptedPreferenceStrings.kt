package com.wirepilot.app.control

import java.nio.ByteBuffer

object EncryptedPreferenceStrings {
  private const val STRING_TYPE = 0

  fun encode(value: String): ByteArray {
    val text = value.toByteArray(Charsets.UTF_8)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES + text.size)
    buffer.putInt(STRING_TYPE)
    buffer.putInt(text.size)
    buffer.put(text)
    return buffer.array()
  }

  fun decode(bytes: ByteArray): String? {
    if (bytes.size < Int.SIZE_BYTES + Int.SIZE_BYTES) {
      return null
    }
    val buffer = ByteBuffer.wrap(bytes)
    if (buffer.int != STRING_TYPE) {
      return null
    }
    val length = buffer.int
    if (length < 0 || length != buffer.remaining()) {
      return null
    }
    val text = ByteArray(length)
    buffer.get(text)
    return text.toString(Charsets.UTF_8)
  }
}
