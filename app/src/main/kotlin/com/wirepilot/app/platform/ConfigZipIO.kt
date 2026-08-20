package com.wirepilot.app.platform

import com.wireguard.config.Config
import com.wirepilot.app.control.ConfigZipLimits
import com.wirepilot.app.control.ConfigZipNames
import com.wirepilot.app.control.SplitTunnelPolicy
import com.wirepilot.app.data.SplitTunnelStore
import com.wirepilot.app.data.StoredSplitTunnel
import com.wirepilot.app.data.TunnelCatalog
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigZipIO {
  fun peekNames(
    input: InputStream,
    isZip: Boolean,
    fallbackName: String,
  ): List<String> {
    return if (isZip) {
      val names = mutableListOf<String>()
      ZipInputStream(input).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          if (entry.isDirectory) {
            continue
          }
          val name = ConfigZipNames.tunnelNameFromPath(entry.name) ?: continue
          names += name
        }
      }
      names.distinct().sorted()
    } else {
      val name = ConfigZipNames.tunnelNameFromPath(fallbackName)
        ?: ConfigZipNames.tunnelNameFromPath("$fallbackName.conf")
      if (name == null) emptyList() else listOf(name)
    }
  }

  fun importAll(
    input: InputStream,
    isZip: Boolean,
    fallbackName: String,
    catalog: TunnelCatalog,
    splitTunnels: SplitTunnelStore? = null,
  ): List<String> {
    val imported = mutableListOf<String>()
    if (isZip) {
      ZipInputStream(input).use { zip ->
        val reader = BufferedReader(InputStreamReader(zip, StandardCharsets.UTF_8))
        var entryIndex = 0
        var totalBytes = 0
        while (true) {
          val entry = zip.nextEntry ?: break
          if (entry.isDirectory) {
            continue
          }
          val name = ConfigZipNames.tunnelNameFromPath(entry.name) ?: continue
          val conf = readLimited(reader, ConfigZipLimits.MAX_ENTRY_BYTES) ?: continue
          if (!ConfigZipLimits.acceptEntry(entryIndex, conf.length, totalBytes + conf.length)) {
            continue
          }
          val parsed = parseOrNull(conf) ?: continue
          catalog.writeConf(name, parsed.toWgQuickString())
          seedSplit(splitTunnels, name, parsed)
          imported += name
          entryIndex += 1
          totalBytes += conf.length
        }
      }
    } else {
      val name = ConfigZipNames.tunnelNameFromPath(fallbackName)
        ?: ConfigZipNames.tunnelNameFromPath("$fallbackName.conf")
        ?: return emptyList()
      val conf = readLimited(input.reader(StandardCharsets.UTF_8), ConfigZipLimits.MAX_ENTRY_BYTES)
        ?: return emptyList()
      if (!ConfigZipLimits.acceptEntry(0, conf.length, conf.length)) {
        return emptyList()
      }
      val parsed = parseOrNull(conf) ?: return emptyList()
      catalog.writeConf(name, parsed.toWgQuickString())
      seedSplit(splitTunnels, name, parsed)
      imported += name
    }
    return imported.distinct().sorted()
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

  private fun seedSplit(splitTunnels: SplitTunnelStore?, name: String, parsed: Config) {
    if (splitTunnels == null) {
      return
    }
    val excluded = parsed.`interface`.excludedApplications
    val included = parsed.`interface`.includedApplications
    splitTunnels.write(
      name,
      StoredSplitTunnel(
        mode = SplitTunnelPolicy.modeFrom(excluded, included),
        packages = excluded + included,
      ),
    )
  }

  fun parseOrNull(conf: String): Config? {
    return runCatching { Config.parse(conf.byteInputStream()) }.getOrNull()
  }

  private fun readLimited(reader: Reader, maxBytes: Int): String? {
    val builder = StringBuilder()
    val buffer = CharArray(1024)
    var total = 0
    while (true) {
      val count = reader.read(buffer)
      if (count < 0) {
        break
      }
      total += count
      if (total > maxBytes) {
        return null
      }
      builder.appendRange(buffer, 0, count)
    }
    return builder.toString()
  }
}
