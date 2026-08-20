package com.wirepilot.app.platform

import android.content.Context
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.data.TunnelCatalog
import java.io.File

class FileTunnelCatalog(
  context: Context,
) : TunnelCatalog {
  private val directory = File(context.filesDir, "tunnels").apply { mkdirs() }

  override fun names(): List<String> {
    return directory.listFiles()
      ?.mapNotNull { file -> ConfigZipNames.tunnelNameFromPath(file.name) }
      ?.sorted()
      .orEmpty()
  }

  override fun readConf(name: String): String? {
    val file = fileFor(name) ?: return null
    return if (file.isFile) file.readText() else null
  }

  override fun writeConf(name: String, conf: String) {
    val file = fileFor(name) ?: return
    file.writeText(conf)
  }

  override fun delete(name: String) {
    fileFor(name)?.delete()
  }

  private fun fileFor(name: String): File? {
    if (!ConfigZipNames.isValidTunnelName(name)) {
      return null
    }
    return File(directory, ConfigZipNames.fileName(name))
  }
}
