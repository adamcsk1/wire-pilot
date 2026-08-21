package com.wirepilot.app.platform

import com.wireguard.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigDraftIOTest {
  @Test
  fun generateAndDerivePublicKey() {
    val generated = ConfigDraftIO.generateKeyPair()
    assertEquals(generated.publicKey, ConfigDraftIO.publicKeyFrom(generated.privateKey))
    assertNull(ConfigDraftIO.publicKeyFrom("nope"))
  }

  @Test
  fun roundTripOnePeer() {
    val local = KeyPair()
    val remote = KeyPair()
    val conf = """
      [Interface]
      PrivateKey = ${local.privateKey.toBase64()}
      Address = 10.0.0.2/32
      DNS = 1.1.1.1
      ListenPort = 51820
      MTU = 1420

      [Peer]
      PublicKey = ${remote.publicKey.toBase64()}
      AllowedIPs = 0.0.0.0/0
      Endpoint = example.com:51820
      PersistentKeepalive = 25
    """.trimIndent()
    val draft = ConfigDraftIO.fromConf("office", conf)
    assertNotNull(draft)
    assertEquals("office", draft.name)
    assertEquals(local.privateKey.toBase64(), draft.privateKey)
    assertEquals(local.publicKey.toBase64(), draft.publicKey)
    assertEquals("10.0.0.2/32", draft.addresses)
    assertEquals("51820", draft.listenPort)
    assertEquals("1.1.1.1", draft.dns)
    assertEquals("1420", draft.mtu)
    assertEquals(1, draft.peers.size)
    assertEquals(remote.publicKey.toBase64(), draft.peers[0].publicKey)
    assertEquals("0.0.0.0/0", draft.peers[0].allowedIps)
    assertEquals("example.com:51820", draft.peers[0].endpoint)
    assertEquals("25", draft.peers[0].persistentKeepalive)
    val written = ConfigDraftIO.toConf(draft).getOrThrow()
    val again = ConfigDraftIO.fromConf("office", written)
    assertEquals(draft.copy(peers = draft.peers), again)
  }

  @Test
  fun toConfRejectsBlankAddress() {
    val local = KeyPair()
    val remote = KeyPair()
    assertTrue(
      ConfigDraftIO.toConf(
        TunnelDraft(
          privateKey = local.privateKey.toBase64(),
          peers = listOf(PeerDraft(publicKey = remote.publicKey.toBase64())),
        ),
      ).isFailure,
    )
  }

  @Test
  fun toConfRejectsMissingPeer() {
    val local = KeyPair()
    assertTrue(
      ConfigDraftIO.toConf(
        TunnelDraft(
          privateKey = local.privateKey.toBase64(),
          addresses = "10.0.0.2/32",
          peers = listOf(PeerDraft()),
        ),
      ).isFailure,
    )
  }

  @Test
  fun toConfRejectsBadKeyAndSkipsBlankPeer() {
    assertTrue(ConfigDraftIO.toConf(TunnelDraft(privateKey = "bad")).isFailure)
    val local = KeyPair()
    val remote = KeyPair()
    val result = ConfigDraftIO.toConf(
      TunnelDraft(
        privateKey = local.privateKey.toBase64(),
        addresses = "10.8.0.2/32",
        peers = listOf(
          PeerDraft(),
          PeerDraft(publicKey = remote.publicKey.toBase64(), allowedIps = "0.0.0.0/0"),
        ),
      ),
    )
    val parsed = ConfigZipIO.parseOrNull(result.getOrThrow())
    assertNotNull(parsed)
    assertEquals(1, parsed.peers.size)
  }

  @Test
  fun fromConfRejectsGarbage() {
    assertNull(ConfigDraftIO.fromConf("office", "not a conf"))
  }
}
