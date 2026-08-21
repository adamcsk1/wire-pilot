package com.wirepilot.app.control

import com.wirepilot.app.data.SsidNormalizer

data class NetworkObservation(
  val link: InventoryLink,
  val probe: SsidProbeLink,
)

data class NetworkObservationState(
  val observations: List<NetworkObservation>,
  val readableRevision: Long,
)

data class NetworkTransportObservation(
  val wifi: Boolean,
  val cellular: Boolean,
  val vpn: Boolean,
  val transportClass: String,
  val ssidRaw: String?,
  val wifiSsidRaw: String?,
)

object NetworkObservationProjector {
  fun project(transport: NetworkTransportObservation): NetworkObservation? {
    if (transport.vpn) {
      return null
    }
    return NetworkObservation(
      link = InventoryLink(
        wifi = transport.wifi,
        cellular = transport.cellular,
        rawSsid = if (transport.wifi) preferredRawSsid(transport) else null,
      ),
      probe = SsidProbeLink(
        wifi = transport.wifi,
        cellular = transport.cellular,
        vpn = false,
        transportClass = transport.transportClass,
        ssidRaw = transport.ssidRaw,
        wifiSsidRaw = transport.wifiSsidRaw,
      ),
    )
  }

  private fun preferredRawSsid(transport: NetworkTransportObservation): String? {
    return when {
      SsidNormalizer.normalize(transport.ssidRaw) != null -> transport.ssidRaw
      SsidNormalizer.normalize(transport.wifiSsidRaw) != null -> transport.wifiSsidRaw
      else -> transport.ssidRaw ?: transport.wifiSsidRaw
    }
  }
}

object NetworkObservationMerger {
  fun refreshAuthoritative(
    existing: NetworkObservation,
    candidate: NetworkObservation,
  ): NetworkObservation {
    return candidate.copy(
      link = candidate.link.copy(rawSsid = existing.link.rawSsid),
      probe = candidate.probe.copy(
        ssidRaw = existing.probe.ssidRaw,
        wifiSsidRaw = existing.probe.wifiSsidRaw,
      ),
    )
  }

}

class NetworkObservationLedger<Key> {
  private val observations = mutableMapOf<Key, NetworkObservation>()
  private val revisions = mutableMapOf<Key, Long>()
  private val sources = mutableMapOf<Key, Source>()
  private var nextRevision = 0L
  private var authoritativeRevision = 0L
  private var scanGeneration = 0L
  private var readableRevision = 0L

  @Synchronized
  fun beginRefresh(key: Key): Long {
    val revision = newRevision()
    revisions[key] = revision
    return revision
  }

  @Synchronized
  fun refresh(key: Key, revision: Long, candidate: NetworkObservation?) {
    if (revisions[key] != revision) {
      return
    }
    when (sources[key]) {
      Source.TOMBSTONE -> return
      Source.CALLBACK -> {
        if (candidate != null) {
          observations[key]?.let { existing ->
            observations[key] = NetworkObservationMerger.refreshAuthoritative(existing, candidate)
          }
        }
      }
      Source.QUERY, null -> {
        if (candidate == null) {
          observations.remove(key)
          sources.remove(key)
        } else {
          observations[key] = candidate
          sources[key] = Source.QUERY
          recordReadable(candidate)
        }
      }
    }
  }

  @Synchronized
  fun observe(key: Key, observation: NetworkObservation?) {
    revisions[key] = newRevision()
    authoritativeRevision += 1L
    if (observation == null) {
      observations.remove(key)
      sources[key] = Source.TOMBSTONE
    } else {
      observations[key] = observation
      sources[key] = Source.CALLBACK
      recordReadable(observation)
    }
  }

  @Synchronized
  fun beginScan(): ScanToken {
    scanGeneration += 1L
    return ScanToken(scanGeneration, authoritativeRevision)
  }

  @Synchronized
  fun removeMissing(keys: Set<Key>, scanToken: ScanToken) {
    if (scanGeneration != scanToken.generation || authoritativeRevision != scanToken.authoritativeRevision) {
      return
    }
    observations.keys.filter { key -> key !in keys }.forEach { key ->
      observations.remove(key)
      revisions[key] = newRevision()
      sources.remove(key)
    }
  }

  @Synchronized
  fun keys(): Set<Key> = observations.keys.toSet()

  @Synchronized
  fun values(): List<NetworkObservation> = observations.values.toList()

  @Synchronized
  fun state(): NetworkObservationState {
    return NetworkObservationState(observations.values.toList(), readableRevision)
  }

  private fun newRevision(): Long {
    nextRevision += 1L
    return nextRevision
  }

  private fun recordReadable(observation: NetworkObservation) {
    if (SsidNormalizer.normalize(observation.link.rawSsid) != null) {
      readableRevision += 1L
    }
  }

  data class ScanToken(
    val generation: Long,
    val authoritativeRevision: Long,
  )

  private enum class Source {
    CALLBACK,
    QUERY,
    TOMBSTONE,
  }
}

object LastKnownRememberPolicy {
  fun shouldRemember(
    snapshot: NetworkSnapshot,
    readableRevision: Long,
    rememberedRevision: Long,
  ): Boolean {
    if (snapshot.kind != NetworkKind.WIFI) {
      return false
    }
    if (snapshot.ssidSource == "connectionInfo") {
      return true
    }
    return readableRevision > rememberedRevision
  }
}
