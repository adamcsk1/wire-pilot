package com.wirepilot.app.platform

import com.wireguard.config.Config
import com.wirepilot.app.control.ConfigZipLimits
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.control.SplitTunnelPolicy
import com.wirepilot.app.data.ConfigImportBatch
import com.wirepilot.app.data.ImportedTunnel
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigZipIO {
  fun readImport(
    input: InputStream,
    isZip: Boolean,
    fallbackName: String,
  ): ConfigImportBatch? {
    return if (isZip) {
      val imported = mutableListOf<ImportedTunnel>()
      var totalBytes = 0
      var entryIndex = 0
      ZipInputStream(input).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          if (entryIndex >= ConfigZipLimits.MAX_ENTRIES) {
            return null
          }
          entryIndex += 1
          if (entry.isDirectory) {
            continue
          }
          val name = ConfigZipNames.tunnelNameFromPath(entry.name) ?: return null
          val bytes = readLimited(zip, ConfigZipLimits.MAX_ENTRY_BYTES) ?: return null
          totalBytes += bytes.size
          if (!ConfigZipLimits.acceptEntry(entryIndex - 1, bytes.size, totalBytes)) {
            return null
          }
          val importedTunnel = importedTunnel(name, bytes) ?: return null
          if (imported.any { tunnel -> tunnel.name == name }) {
            return null
          }
          imported += importedTunnel
        }
      }
      ConfigImportBatch(imported.sortedBy { tunnel -> tunnel.name }).takeIf { batch -> batch.tunnels.isNotEmpty() }
    } else {
      val name = ConfigZipNames.tunnelNameFromPath(fallbackName)
        ?: ConfigZipNames.tunnelNameFromPath("$fallbackName.conf")
        ?: return null
      val bytes = readLimited(input, ConfigZipLimits.MAX_ENTRY_BYTES) ?: return null
      if (!ConfigZipLimits.acceptEntry(0, bytes.size, bytes.size)) {
        return null
      }
      importedTunnel(name, bytes)?.let { tunnel -> ConfigImportBatch(listOf(tunnel)) }
    }
  }

  fun exportZip(
    output: OutputStream,
    catalog: TunnelCatalog,
    names: List<String>,
    confFor: (String, String) -> String = { _, conf -> conf },
  ) {
    ZipOutputStream(output).use { zip ->
      names.forEach { name ->
        val stored = catalog.readConf(name) ?: return@forEach
        val conf = confFor(name, stored)
        zip.putNextEntry(ZipEntry(ConfigZipNames.fileName(name)))
        zip.write(conf.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
      }
    }
  }

  private fun importedTunnel(name: String, bytes: ByteArray): ImportedTunnel? {
    val conf = decodeUtf8(bytes) ?: return null
    val parsed = parseOrNull(conf) ?: return null
    val excluded = parsed.`interface`.excludedApplications
    val included = parsed.`interface`.includedApplications
    return ImportedTunnel(
      name = name,
      conf = parsed.toWgQuickString(),
      splitTunnel = StoredSplitTunnel(
        mode = SplitTunnelPolicy.modeFrom(excluded, included),
        packages = excluded + included,
      ),
    )
  }

  fun parseOrNull(conf: String): Config? {
    return runCatching { Config.parse(conf.byteInputStream()) }.getOrNull()
  }

  private fun readLimited(input: InputStream, maxBytes: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var total = 0
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      total += count
      if (total > maxBytes) return null
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  private fun decodeUtf8(bytes: ByteArray): String? {
    return runCatching {
      StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()
    }.getOrNull()
  }
}
