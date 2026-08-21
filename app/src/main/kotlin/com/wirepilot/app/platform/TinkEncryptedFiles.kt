package com.wirepilot.app.platform

import android.content.Context
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File

class TinkEncryptedFiles(
  context: Context,
) {
  private val streamingAead: StreamingAead

  init {
    StreamingAeadConfig.register()
    streamingAead = AndroidKeysetManager.Builder()
      .withSharedPref(context.applicationContext, TinkKeys.FILE_KEYSET_ALIAS, TinkKeys.FILE_KEYSET_PREF)
      .withKeyTemplate(KeyTemplate.createFrom(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB))
      .withMasterKeyUri(TinkKeys.MASTER_KEY_URI)
      .build()
      .keysetHandle
      .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
  }

  fun read(file: File): String? {
    if (!file.isFile) {
      return null
    }
    return runCatching {
      file.inputStream().use { input ->
        streamingAead.newDecryptingStream(input, associatedData(file)).use { decrypted ->
          decrypted.readBytes().toString(Charsets.UTF_8)
        }
      }
    }.getOrNull()
  }

  fun write(file: File, plaintext: String) {
    file.outputStream().use { output ->
      streamingAead.newEncryptingStream(output, associatedData(file)).use { encrypted ->
        encrypted.write(plaintext.toByteArray(Charsets.UTF_8))
      }
    }
  }

  private fun associatedData(file: File): ByteArray {
    return file.name.toByteArray(Charsets.UTF_8)
  }
}
