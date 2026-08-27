package com.wirepilot.app.platform

import android.content.Context
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.control.LeftoverPlaintextConf
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

  init {
    encryptLeftoverPlaintext()
  }

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
      tryEncryptLeftover(file)
      readEncrypted(file)?.let { plaintext ->
        siblingTemp(file).delete()
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

  private fun encryptLeftoverPlaintext() {
    directory.listFiles()?.forEach { file ->
      if (!file.isFile || ConfigZipNames.tunnelNameFromPath(file.name) == null) {
        return@forEach
      }
      tryEncryptLeftover(file)
    }
  }

  private fun tryEncryptLeftover(file: File) {
    val encryptedReadable = readEncrypted(file) != null
    val plaintext = if (encryptedReadable) {
      null
    } else {
      runCatching { file.readText() }.getOrNull()
    }
    val plaintextParses = plaintext != null && ConfigZipIO.parseOrNull(plaintext) != null
    if (!LeftoverPlaintextConf.shouldEncrypt(encryptedReadable, plaintextParses) || plaintext == null) {
      return
    }
    runCatching { writeEncrypted(file, plaintext) }
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
