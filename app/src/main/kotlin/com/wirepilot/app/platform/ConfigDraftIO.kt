package com.wirepilot.app.platform

import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

data class TunnelDraft(
  val name: String = "",
  val privateKey: String = "",
  val publicKey: String = "",
  val addresses: String = "",
  val listenPort: String = "",
  val dns: String = "",
  val mtu: String = "",
  val peers: List<PeerDraft> = listOf(PeerDraft()),
)

data class PeerDraft(
  val publicKey: String = "",
  val presharedKey: String = "",
  val allowedIps: String = "",
  val endpoint: String = "",
  val persistentKeepalive: String = "",
) {
  fun isBlank(): Boolean {
    return publicKey.isBlank() &&
      presharedKey.isBlank() &&
      allowedIps.isBlank() &&
      endpoint.isBlank() &&
      persistentKeepalive.isBlank()
  }
}

data class GeneratedKeyPair(
  val privateKey: String,
  val publicKey: String,
)

object ConfigDraftIO {
  fun generateKeyPair(): GeneratedKeyPair {
    val pair = KeyPair()
    return GeneratedKeyPair(
      privateKey = pair.privateKey.toBase64(),
      publicKey = pair.publicKey.toBase64(),
    )
  }

  fun publicKeyFrom(privateKey: String): String? {
    return runCatching {
      KeyPair(Key.fromBase64(privateKey.trim())).publicKey.toBase64()
    }.getOrNull()
  }

  fun fromConf(name: String, conf: String): TunnelDraft? {
    val parsed = ConfigZipIO.parseOrNull(conf) ?: return null
    val iface = parsed.`interface`
    val dnsServers = iface.dnsServers.mapNotNull { address ->
      address.hostAddress ?: address.hostName
    }
    return TunnelDraft(
      name = name,
      privateKey = iface.keyPair.privateKey.toBase64(),
      publicKey = iface.keyPair.publicKey.toBase64(),
      addresses = iface.addresses.joinToString(", "),
      listenPort = iface.listenPort.map { port -> port.toString() }.orElse(""),
      dns = (dnsServers + iface.dnsSearchDomains).joinToString(", "),
      mtu = iface.mtu.map { value -> value.toString() }.orElse(""),
      peers = parsed.peers.map { peer ->
        PeerDraft(
          publicKey = peer.publicKey.toBase64(),
          presharedKey = peer.preSharedKey.map { key -> key.toBase64() }.orElse(""),
          allowedIps = peer.allowedIps.joinToString(", "),
          endpoint = peer.endpoint.map { value -> value.toString() }.orElse(""),
          persistentKeepalive = peer.persistentKeepalive.map { value -> value.toString() }.orElse(""),
        )
      }.ifEmpty { listOf(PeerDraft()) },
    )
  }

  fun toConf(draft: TunnelDraft): Result<String> {
    return runCatching {
      val ifaceBuilder = Interface.Builder()
      ifaceBuilder.parsePrivateKey(draft.privateKey.trim())
      if (draft.addresses.isBlank()) {
        error("address required")
      }
      ifaceBuilder.parseAddresses(draft.addresses.trim())
      if (draft.dns.isNotBlank()) {
        ifaceBuilder.parseDnsServers(draft.dns.trim())
      }
      if (draft.listenPort.isNotBlank()) {
        ifaceBuilder.parseListenPort(draft.listenPort.trim())
      }
      if (draft.mtu.isNotBlank()) {
        ifaceBuilder.parseMtu(draft.mtu.trim())
      }
      val peers = draft.peers.filterNot { peer -> peer.isBlank() }
      if (peers.isEmpty()) {
        error("peer required")
      }
      val configBuilder = Config.Builder().setInterface(ifaceBuilder.build())
      peers.forEach { peer ->
        val peerBuilder = Peer.Builder()
        peerBuilder.parsePublicKey(peer.publicKey.trim())
        if (peer.presharedKey.isNotBlank()) {
          peerBuilder.parsePreSharedKey(peer.presharedKey.trim())
        }
        if (peer.allowedIps.isNotBlank()) {
          peerBuilder.parseAllowedIPs(peer.allowedIps.trim())
        }
        if (peer.endpoint.isNotBlank()) {
          peerBuilder.parseEndpoint(peer.endpoint.trim())
        }
        if (peer.persistentKeepalive.isNotBlank()) {
          peerBuilder.parsePersistentKeepalive(peer.persistentKeepalive.trim())
        }
        configBuilder.addPeer(peerBuilder.build())
      }
      configBuilder.build().toWgQuickString()
    }
  }
}
