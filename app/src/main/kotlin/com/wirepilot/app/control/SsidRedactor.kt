package com.wirepilot.app.control

object SsidRedactor {
  fun redact(value: String): String {
    var hash = 0
    value.forEach { char ->
      hash = 31 * hash + char.code
    }
    return "h%04x".format(hash and 0xFFFF)
  }

  fun redactNullable(value: String?): String {
    return if (value == null) "null" else redact(value)
  }
}
