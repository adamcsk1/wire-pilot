package com.wirepilot.app.platform

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.daead.PredefinedDeterministicAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.wirepilot.app.control.EncryptedPreferenceStrings

class TinkEncryptedPreferences(
  context: Context,
  private val fileName: String,
) : SharedPreferences {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
  private val fileNameBytes = fileName.toByteArray(Charsets.UTF_8)
  private val keyAead: DeterministicAead
  private val valueAead: Aead

  init {
    AeadConfig.register()
    DeterministicAeadConfig.register()
    keyAead = AndroidKeysetManager.Builder()
      .withSharedPref(appContext, TinkKeys.PREFS_KEY_KEYSET, fileName)
      .withKeyTemplate(KeyTemplate.createFrom(PredefinedDeterministicAeadParameters.AES256_SIV))
      .withMasterKeyUri(TinkKeys.MASTER_KEY_URI)
      .build()
      .keysetHandle
      .getPrimitive(RegistryConfiguration.get(), DeterministicAead::class.java)
    valueAead = AndroidKeysetManager.Builder()
      .withSharedPref(appContext, TinkKeys.PREFS_VALUE_KEYSET, fileName)
      .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
      .withMasterKeyUri(TinkKeys.MASTER_KEY_URI)
      .build()
      .keysetHandle
      .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
  }

  override fun getAll(): MutableMap<String, *> {
    val values = mutableMapOf<String, String>()
    preferences.all.forEach { (encryptedKey, encryptedValue) ->
      if (encryptedKey == null || isReserved(encryptedKey) || encryptedValue !is String) {
        return@forEach
      }
      val plainKey = decryptKey(encryptedKey) ?: return@forEach
      values[plainKey] = decryptValue(encryptedKey, encryptedValue)
    }
    return values
  }

  override fun getString(key: String?, defaultValue: String?): String? {
    if (key == null || isReserved(key)) {
      return defaultValue
    }
    val encryptedKey = encryptKey(key)
    val encryptedValue = preferences.getString(encryptedKey, null) ?: return defaultValue
    return decryptValue(encryptedKey, encryptedValue)
  }

  override fun getStringSet(key: String?, defaultValue: MutableSet<String>?): MutableSet<String>? {
    throw UnsupportedOperationException()
  }

  override fun getInt(key: String?, defaultValue: Int): Int = throw UnsupportedOperationException()

  override fun getLong(key: String?, defaultValue: Long): Long = throw UnsupportedOperationException()

  override fun getFloat(key: String?, defaultValue: Float): Float = throw UnsupportedOperationException()

  override fun getBoolean(key: String?, defaultValue: Boolean): Boolean = throw UnsupportedOperationException()

  override fun contains(key: String?): Boolean {
    if (key == null || isReserved(key)) {
      return false
    }
    return preferences.contains(encryptKey(key))
  }

  override fun edit(): SharedPreferences.Editor = Editor()

  override fun registerOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?,
  ) = Unit

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?,
  ) = Unit

  private fun encryptKey(key: String): String {
    return Base64.encodeToString(
      keyAead.encryptDeterministically(key.toByteArray(Charsets.UTF_8), fileNameBytes),
      Base64.NO_WRAP,
    )
  }

  private fun decryptKey(encryptedKey: String): String? {
    return runCatching {
      keyAead.decryptDeterministically(
        Base64.decode(encryptedKey, Base64.NO_WRAP),
        fileNameBytes,
      ).toString(Charsets.UTF_8)
    }.getOrNull()
  }

  private fun decryptValue(encryptedKey: String, encryptedValue: String): String {
    val decrypted = valueAead.decrypt(
      Base64.decode(encryptedValue, Base64.NO_WRAP),
      encryptedKey.toByteArray(Charsets.UTF_8),
    )
    return EncryptedPreferenceStrings.decode(decrypted)
      ?: error("encrypted preference is not a string")
  }

  private fun isReserved(key: String): Boolean {
    return key == TinkKeys.PREFS_KEY_KEYSET || key == TinkKeys.PREFS_VALUE_KEYSET
  }

  private inner class Editor : SharedPreferences.Editor {
    private val delegate = preferences.edit()

    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
      if (key == null || isReserved(key)) {
        return this
      }
      if (value == null) {
        return remove(key)
      }
      val encryptedKey = encryptKey(key)
      val encryptedValue = Base64.encodeToString(
        valueAead.encrypt(
          EncryptedPreferenceStrings.encode(value),
          encryptedKey.toByteArray(Charsets.UTF_8),
        ),
        Base64.NO_WRAP,
      )
      delegate.putString(encryptedKey, encryptedValue)
      return this
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
      throw UnsupportedOperationException()
    }

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = throw UnsupportedOperationException()

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = throw UnsupportedOperationException()

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = throw UnsupportedOperationException()

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
      throw UnsupportedOperationException()
    }

    override fun remove(key: String?): SharedPreferences.Editor {
      if (key == null || isReserved(key)) {
        return this
      }
      delegate.remove(encryptKey(key))
      return this
    }

    override fun clear(): SharedPreferences.Editor = throw UnsupportedOperationException()

    override fun commit(): Boolean = delegate.commit()

    override fun apply() {
      delegate.apply()
    }
  }
}
