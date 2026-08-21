package com.wirepilot.app.platform

import android.content.Context
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.data.TunnelCatalog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileTunnelCatalog(
  context: Context,
) : TunnelCatalog {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, "tunnels").apply { mkdirs() }
  private val stagingDirectory = File(directory, ".staging").apply { mkdirs() }
  private val files = TinkEncryptedFiles(appContext)

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
        return try {
          writeEncrypted(file, plaintext)
          plaintext
        } catch (_: IOException) {
          null
        }
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

  private fun readEncrypted(file: File): String? {
    return files.read(file)
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
    files.write(staging, conf)
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
