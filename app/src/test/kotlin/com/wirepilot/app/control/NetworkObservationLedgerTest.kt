package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkObservationLedgerTest {
  @Test
  fun authoritativeCallbackRejectsOlderRefresh() {
    val ledger = NetworkObservationLedger<String>()
    val refreshRevision = ledger.beginRefresh("wifi")
    val callback = observation("New")

    ledger.observe("wifi", callback)
    ledger.refresh("wifi", refreshRevision, observation("Old"))

    assertEquals(listOf(callback), ledger.values())
  }

  @Test
  fun lossRejectsOlderRefreshWithoutResurrection() {
    val ledger = NetworkObservationLedger<String>()
    ledger.observe("wifi", observation("Home"))
    val refreshRevision = ledger.beginRefresh("wifi")

    ledger.observe("wifi", null)
    ledger.refresh("wifi", refreshRevision, observation("Home"))

    assertEquals(emptyList(), ledger.values())
  }

  @Test
  fun latestRefreshWinsWhenQueriesOverlap() {
    val ledger = NetworkObservationLedger<String>()
    val oldRevision = ledger.beginRefresh("wifi")
    val newRevision = ledger.beginRefresh("wifi")

    ledger.refresh("wifi", newRevision, observation("New"))
    ledger.refresh("wifi", oldRevision, observation("Old"))

    assertEquals(listOf(observation("New")), ledger.values())
  }

  @Test
  fun callbackCanReplaceReadableObservationWithRedactedState() {
    val ledger = NetworkObservationLedger<String>()
    val redacted = observation("<unknown ssid>")
    ledger.observe("wifi", observation("Home"))

    ledger.observe("wifi", redacted)

    assertEquals(listOf(redacted), ledger.values())
  }

  @Test
  fun refreshAfterCallbackCannotReplaceAuthoritativeSsid() {
    val ledger = NetworkObservationLedger<String>()
    val callback = observation("Callback")
    ledger.observe("wifi", callback)
    val revision = ledger.beginRefresh("wifi")
    val query = observation("Query").copy(
      link = InventoryLink(wifi = true, cellular = true, rawSsid = "Query"),
      probe = SsidProbeLink(
        wifi = true,
        cellular = true,
        vpn = false,
        transportClass = "QueryWifiInfo",
        ssidRaw = "Query",
        wifiSsidRaw = "Query",
      ),
    )

    ledger.refresh("wifi", revision, query)

    val result = ledger.values().single()
    assertEquals("Callback", result.link.rawSsid)
    assertEquals(true, result.link.cellular)
    assertEquals("QueryWifiInfo", result.probe.transportClass)
  }

  @Test
  fun refreshAfterLossCannotResurrectNetwork() {
    val ledger = NetworkObservationLedger<String>()
    ledger.observe("wifi", observation("Home"))
    ledger.observe("wifi", null)
    val revision = ledger.beginRefresh("wifi")

    ledger.refresh("wifi", revision, observation("Home"))

    assertEquals(emptyList(), ledger.values())
  }

  @Test
  fun currentNullRefreshRemovesLostCallbackNetwork() {
    val ledger = NetworkObservationLedger<String>()
    ledger.observe("wifi", observation("Home"))
    val revision = ledger.beginRefresh("wifi")

    ledger.refresh("wifi", revision, null)
    ledger.refresh("wifi", ledger.beginRefresh("wifi"), observation("Home"))

    assertEquals(emptyList(), ledger.values())
  }

  @Test
  fun currentNullRefreshRemovesOnlyTargetQueryObservation() {
    val ledger = NetworkObservationLedger<String>()
    val homeRevision = ledger.beginRefresh("home")
    ledger.refresh("home", homeRevision, observation("Home"))
    val cafeRevision = ledger.beginRefresh("cafe")
    ledger.refresh("cafe", cafeRevision, observation("Cafe"))
    val removalRevision = ledger.beginRefresh("home")

    ledger.refresh("home", removalRevision, null)

    assertEquals(setOf("cafe"), ledger.keys())
  }

  @Test
  fun staleNullRefreshCannotEraseNewerCallback() {
    val ledger = NetworkObservationLedger<String>()
    val staleRevision = ledger.beginRefresh("wifi")
    ledger.observe("wifi", observation("Home"))

    ledger.refresh("wifi", staleRevision, null)

    assertEquals(listOf(observation("Home")), ledger.values())
  }

  @Test
  fun removingOneNetworkLeavesOtherWifiObservation() {
    val ledger = NetworkObservationLedger<String>()
    ledger.observe("home", observation("Home"))
    ledger.observe("cafe", observation("Cafe"))

    ledger.observe("home", null)

    assertEquals(setOf("cafe"), ledger.keys())
    assertEquals(listOf(observation("Cafe")), ledger.values())
  }

  @Test
  fun staleScanCannotRemoveNetworkAddedByCallback() {
    val ledger = NetworkObservationLedger<String>()
    val scanRevision = ledger.beginScan()
    ledger.observe("wifi", observation("Home"))

    ledger.removeMissing(emptySet(), scanRevision)

    assertEquals(setOf("wifi"), ledger.keys())
  }

  @Test
  fun currentScanRemovesMissingNetworks() {
    val ledger = NetworkObservationLedger<String>()
    ledger.observe("home", observation("Home"))
    ledger.observe("cafe", observation("Cafe"))
    val scanRevision = ledger.beginScan()

    ledger.removeMissing(setOf("cafe"), scanRevision)

    assertEquals(setOf("cafe"), ledger.keys())
  }

  @Test
  fun newerScanPreventsOlderScanFromRemovingNetworks() {
    val ledger = NetworkObservationLedger<String>()
    val revision = ledger.beginRefresh("wifi")
    ledger.refresh("wifi", revision, observation("Home"))
    val oldScan = ledger.beginScan()
    val newScan = ledger.beginScan()

    ledger.removeMissing(setOf("wifi"), newScan)
    ledger.removeMissing(emptySet(), oldScan)

    assertEquals(setOf("wifi"), ledger.keys())
  }

  @Test
  fun scanRemovalInvalidatesInFlightRefresh() {
    val ledger = NetworkObservationLedger<String>()
    val initialRevision = ledger.beginRefresh("wifi")
    ledger.refresh("wifi", initialRevision, observation("Home"))
    val inFlightRevision = ledger.beginRefresh("wifi")
    val scan = ledger.beginScan()

    ledger.removeMissing(emptySet(), scan)
    ledger.refresh("wifi", inFlightRevision, observation("Home"))

    assertEquals(emptySet(), ledger.keys())
  }

  @Test
  fun redactedQueryDoesNotPreserveReadableQuerySsid() {
    val ledger = NetworkObservationLedger<String>()
    val readableRevision = ledger.beginRefresh("wifi")
    ledger.refresh("wifi", readableRevision, observation("Home"))
    val redactedRevision = ledger.beginRefresh("wifi")

    ledger.refresh("wifi", redactedRevision, observation("<unknown ssid>"))

    assertEquals("<unknown ssid>", ledger.values().single().link.rawSsid)
  }

  @Test
  fun onlyFreshReadableObservationsAdvanceReadableRevision() {
    val ledger = NetworkObservationLedger<String>()
    assertEquals(0L, ledger.state().readableRevision)
    val queryRevision = ledger.beginRefresh("wifi")
    ledger.refresh("wifi", queryRevision, observation("Home"))
    assertEquals(1L, ledger.state().readableRevision)
    val redactedRevision = ledger.beginRefresh("wifi")
    ledger.refresh("wifi", redactedRevision, observation("<unknown ssid>"))
    assertEquals(1L, ledger.state().readableRevision)
    ledger.observe("wifi", observation("Cafe"))
    assertEquals(2L, ledger.state().readableRevision)
  }

  private fun observation(ssid: String): NetworkObservation {
    return NetworkObservation(
      link = InventoryLink(wifi = true, cellular = false, rawSsid = ssid),
      probe = SsidProbeLink(
        wifi = true,
        cellular = false,
        vpn = false,
        transportClass = "WifiInfo",
        ssidRaw = ssid,
        wifiSsidRaw = null,
      ),
    )
  }
}
