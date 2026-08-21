package com.wirepilot.app.platform

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.data.TunnelCatalog
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileTunnelCatalog(
  context: Context,
) : TunnelCatalog {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, "tunnels").apply { mkdirs() }
  private val stagingDirectory = File(directory, ".staging").apply { mkdirs() }
  private val masterKey = MasterKey.Builder(appContext)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  override fun names(): List<String> {
    return directory.listFiles()
      ?.mapNotNull { file -> ConfigZipNames.tunnelNameFromPath(file.name) }
      ?.sorted()
      .orEmpty()
  }

  override fun readConf(name: String): String? {
    val file = fileFor(name) ?: return null
    if (file.isFile) {
      readEncrypted(file)?.let { plaintext ->
        siblingTemp(file).delete()
        return plaintext
      }
      val plaintext = runCatching { file.readText() }.getOrNull()
      if (plaintext != null && ConfigZipIO.parseOrNull(plaintext) != null) {
        siblingTemp(file).delete()
        runCatching { writeEncrypted(file, plaintext) }
        return plaintext
      }
      recoverLegacyTempAad(file)?.let { return it }
    }
    return recoverLegacyTempAad(file)
  }

  override fun writeConf(name: String, conf: String) {
    val file = fileFor(name) ?: return
    writeEncrypted(file, conf)
  }

  override fun delete(name: String) {
    val file = fileFor(name) ?: return
    file.delete()
    siblingTemp(file).delete()
    File(stagingDirectory, file.name).delete()
  }

  private fun fileFor(name: String): File? {
    if (!ConfigZipNames.isValidTunnelName(name)) {
      return null
    }
    return File(directory, ConfigZipNames.fileName(name))
  }

  private fun encrypted(file: File): EncryptedFile {
    return EncryptedFile.Builder(
      appContext,
      file,
      masterKey,
      EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()
  }

  private fun readEncrypted(file: File): String? {
    return runCatching {
      encrypted(file).openFileInput().use { input ->
        input.readBytes().toString(StandardCharsets.UTF_8)
      }
    }.getOrNull()
  }

  private fun siblingTemp(file: File): File {
    return File(directory, "${file.name}.tmp")
  }

  private fun recoverLegacyTempAad(file: File): String? {
    val temp = siblingTemp(file)
    if (!temp.isFile) {
      return null
    }
    val plaintext = readEncrypted(temp) ?: return null
    val published = runCatching {
      writeEncrypted(file, plaintext)
      true
    }.getOrDefault(false)
    if (published) {
      temp.delete()
    }
    return plaintext
  }

  private fun writeEncrypted(target: File, conf: String) {
    val staging = File(stagingDirectory, target.name)
    if (staging.exists() && !staging.delete()) {
      throw IOException("could not clear staging ${target.name}")
    }
    encrypted(staging).openFileOutput().use { output ->
      output.write(conf.toByteArray(StandardCharsets.UTF_8))
    }
    if (readEncrypted(staging) == null) {
      staging.delete()
      throw IOException("staging decrypt failed ${target.name}")
    }
    try {
      Files.move(
        staging.toPath(),
        target.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (error: Exception) {
      staging.delete()
      throw IOException("could not publish ${target.name}", error)
    }
  }
}
