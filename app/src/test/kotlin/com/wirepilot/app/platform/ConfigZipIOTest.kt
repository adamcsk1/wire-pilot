package com.wirepilot.app.platform

import com.wireguard.crypto.KeyPair
import com.wirepilot.app.control.ConfigZipLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigZipIOTest {
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
