package com.wirepilot.app.platform

internal object TinkKeys {
  const val MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
  const val PREFS_KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
  const val PREFS_VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
  const val FILE_KEYSET_PREF = "__androidx_security_crypto_encrypted_file_pref__"
  const val FILE_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_file_keyset__"
}
