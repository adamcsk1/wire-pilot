package com.wirepilot.app.platform

import com.wireguard.crypto.KeyPair
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.StoredSplitTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigSplitMergerTest {
  @Test
  fun allAppsLeavesPeerAndInterfaceIntact() {
    val parsed = parsedConf()
    val merged = ConfigSplitMerger.merge(parsed, StoredSplitTunnel())
    assertTrue(merged.`interface`.excludedApplications.isEmpty())
    assertTrue(merged.`interface`.includedApplications.isEmpty())
    assertEquals(parsed.`interface`.addresses, merged.`interface`.addresses)
    assertEquals(parsed.`interface`.dnsServers, merged.`interface`.dnsServers)
    assertEquals(parsed.`interface`.listenPort, merged.`interface`.listenPort)
    assertEquals(parsed.`interface`.mtu, merged.`interface`.mtu)
    assertEquals(parsed.`interface`.keyPair.privateKey, merged.`interface`.keyPair.privateKey)
    assertEquals(1, merged.peers.size)
    assertEquals(parsed.peers[0].allowedIps, merged.peers[0].allowedIps)
  }

  @Test
  fun excludeSetsOnlyExcludedApplications() {
    val merged = ConfigSplitMerger.merge(
      parsedConf(),
      StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo", "com.bar")),
    )
    assertEquals(setOf("com.bar", "com.foo"), merged.`interface`.excludedApplications.toSet())
    assertTrue(merged.`interface`.includedApplications.isEmpty())
  }

  @Test
  fun includeSetsOnlyIncludedApplications() {
    val merged = ConfigSplitMerger.merge(
      parsedConf(),
      StoredSplitTunnel(SplitTunnelMode.INCLUDE_APPS, setOf("com.only")),
    )
    assertTrue(merged.`interface`.excludedApplications.isEmpty())
    assertEquals(setOf("com.only"), merged.`interface`.includedApplications.toSet())
  }

  @Test
  fun allAppsStripsAppsFromParsedConf() {
    val parsed = parsedConf(extraInterfaceLines = "ExcludedApplications = com.old")
    assertEquals(setOf("com.old"), parsed.`interface`.excludedApplications.toSet())
    val merged = ConfigSplitMerger.merge(parsed, StoredSplitTunnel())
    assertTrue(merged.`interface`.excludedApplications.isEmpty())
    assertTrue(merged.`interface`.includedApplications.isEmpty())
  }

  @Test
  fun toConfRoundTripsExcludedApps() {
    val merged = ConfigSplitMerger.merge(
      parsedConf(),
      StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo")),
    )
    val text = ConfigSplitMerger.toConf(merged)
    val again = ConfigZipIO.parseOrNull(text)
    assertNotNull(again)
    assertEquals(setOf("com.foo"), again.`interface`.excludedApplications.toSet())
  }

  private fun parsedConf(extraInterfaceLines: String = ""): com.wireguard.config.Config {
    val local = KeyPair()
    val remote = KeyPair()
    val extra = if (extraInterfaceLines.isBlank()) "" else "\n      $extraInterfaceLines"
    val conf = """
      [Interface]
      PrivateKey = ${local.privateKey.toBase64()}
      Address = 10.0.0.2/32
      DNS = 1.1.1.1
      ListenPort = 51820
      MTU = 1420$extra

      [Peer]
      PublicKey = ${remote.publicKey.toBase64()}
      AllowedIPs = 0.0.0.0/0
      Endpoint = example.com:51820
    """.trimIndent()
    return ConfigZipIO.parseOrNull(conf) ?: error("sample conf must parse")
  }
}
