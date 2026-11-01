package com.wirepilot.app.platform

import com.wireguard.crypto.KeyPair
import com.wirepilot.app.control.ConfigZipLimits
import com.wirepilot.app.support.InMemoryTunnelCatalog
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigZipIOTest {
  @Test
  fun exportFailsWhenAnyRequestedConfigIsUnreadable() {
    val catalog = InMemoryTunnelCatalog(mapOf("office" to config()))

    for (names in listOf(listOf("missing"), listOf("office", "missing"))) {
      assertFailsWith<IOException> { ConfigZipIO.exportZip(ByteArrayOutputStream(), catalog, names) }
    }
  }

  @Test
  fun exportPropagatesWriteFailureWithoutChangingSource() {
    val configs = mapOf("office" to config())
    val catalog = InMemoryTunnelCatalog(configs)
    val output = object : java.io.OutputStream() {
      override fun write(value: Int) {
        throw IOException("destination unavailable")
      }
    }

    assertFailsWith<IOException> { ConfigZipIO.exportZip(output, catalog, listOf("office")) }
    assertEquals(configs.getValue("office"), catalog.readConf("office"))
  }

  @Test
  fun exportedConfigsRoundTripWithoutLoss() {
    val configs = mapOf("office" to config(), "travel" to config())
    val output = ByteArrayOutputStream()
    ConfigZipIO.exportZip(output, InMemoryTunnelCatalog(configs), configs.keys.toList())

    val batch = assertNotNull(ConfigZipIO.readImport(output.toByteArray().inputStream(), true, "backup.zip"))

    assertEquals(configs.keys.toList(), batch.tunnels.map { tunnel -> tunnel.name })
    batch.tunnels.forEach { tunnel ->
      assertEquals(ConfigZipIO.parseOrNull(configs.getValue(tunnel.name))!!.toWgQuickString(), tunnel.conf)
    }
  }

  @Test
  fun acceptsEmptyDirectoriesBeforeConfigs() {
    val batch = assertNotNull(ConfigZipIO.readImport(zip("folder/" to "", "folder/office.conf" to config()).inputStream(), true, "backup.zip"))

    assertEquals(listOf("office"), batch.tunnels.map { tunnel -> tunnel.name })
  }

  @Test
  fun rejectsDirectoryWithAnyPayload() {
    for (payload in listOf("x", "x".repeat(ConfigZipLimits.MAX_ENTRY_BYTES + 1))) {
      assertNull(ConfigZipIO.readImport(zip("folder/" to payload, "office.conf" to config()).inputStream(), true, "backup.zip"))
    }
  }

  @Test
  fun readsSingleConfigOnceAndNormalizesIt() {
    val batch = ConfigZipIO.readImport(ByteArrayInputStream(config().toByteArray()), false, "office.conf")

    assertNotNull(batch)
    assertEquals(listOf("office"), batch.tunnels.map { tunnel -> tunnel.name })
    assertNotNull(ConfigZipIO.parseOrNull(batch.tunnels.single().conf))
  }

  @Test
  fun rejectsMalformedUtf8AndOversizedSingleConfig() {
    assertNull(ConfigZipIO.readImport(ByteArrayInputStream(byteArrayOf(0x80.toByte())), false, "office.conf"))
    assertNull(
      ConfigZipIO.readImport(
        ByteArrayInputStream(ByteArray(ConfigZipLimits.MAX_ENTRY_BYTES + 1)),
        false,
        "office.conf",
      ),
    )
  }

  @Test
  fun rejectsDuplicateAndUnexpectedZipEntries() {
    assertNull(ConfigZipIO.readImport(ByteArrayInputStream(zip("office.conf" to config(), "old/office.conf" to config())), true, "x.zip"))
    assertNull(ConfigZipIO.readImport(ByteArrayInputStream(zip("notes.txt" to "hello")), true, "x.zip"))
  }

  @Test
  fun rejectsZipWithTooManyEntries() {
    val entries = (0..ConfigZipLimits.MAX_ENTRIES).map { index -> "t$index.conf" to config() }
    assertNull(ConfigZipIO.readImport(ByteArrayInputStream(zip(*entries.toTypedArray())), true, "x.zip"))
  }

  @Test
  fun rejectsZipOverTotalUncompressedLimit() {
    val padded = config() + "\n" + "# padding\n".repeat(5_000)
    val entries = (0..10).map { index -> "t$index.conf" to padded }

    assertNull(ConfigZipIO.readImport(ByteArrayInputStream(zip(*entries.toTypedArray())), true, "x.zip"))
  }

  private fun zip(vararg entries: Pair<String, String>): ByteArray {
    return ByteArrayOutputStream().use { bytes ->
      ZipOutputStream(bytes).use { zip ->
        entries.forEach { (name, content) ->
          zip.putNextEntry(ZipEntry(name))
          zip.write(content.toByteArray())
          zip.closeEntry()
        }
      }
      bytes.toByteArray()
    }
  }

  private fun config(): String {
    val local = KeyPair()
    val remote = KeyPair()
    return """
      [Interface]
      PrivateKey = ${local.privateKey.toBase64()}
      Address = 10.0.0.2/32

      [Peer]
      PublicKey = ${remote.publicKey.toBase64()}
      AllowedIPs = 0.0.0.0/0
    """.trimIndent()
  }
}
