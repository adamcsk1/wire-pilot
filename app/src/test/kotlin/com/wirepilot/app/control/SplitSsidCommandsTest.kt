package com.wirepilot.app.control

import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.data.StoredControl
import com.wirepilot.app.support.InMemoryControlStore
import com.wirepilot.app.support.InMemoryExcludedSsidStore
import com.wirepilot.app.support.InMemorySplitTunnelStore
import com.wirepilot.app.support.InMemoryTunnelCatalog
import com.wirepilot.app.support.RecordingTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitSsidCommandsTest {
  private val now = 10_000L

  private fun commands(
    initial: StoredControl = StoredControl(enabled = true, tunnelName = "office"),
  ): Triple<SplitSsidCommands, InMemorySplitTunnelStore, InMemoryExcludedSsidStore> {
    val store = InMemoryControlStore(initial)
    val splits = InMemorySplitTunnelStore()
    val ssids = InMemoryExcludedSsidStore()
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val splitSsids = SplitSsidCommands(
      store = store,
      applyRunner = ApplyRunner(
        store,
        { now },
        { NetworkSnapshot(NetworkKind.MOBILE) },
        RecordingTunnel(),
      ),
      splitTunnels = splits,
      excludedSsids = ssids,
      catalog = catalog,
      resolver = ControlResolver(store) { now },
    )
    return Triple(splitSsids, splits, ssids)
  }

  @Test
  fun setSplitTunnelIgnoresBlankName() {
    val (splitSsids, splits) = commands(StoredControl())
    splitSsids.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"), "")
    assertEquals(com.wirepilot.app.data.StoredSplitTunnel(), splits.read(""))
  }

  @Test
  fun addExcludedSsidRejectsBlankTunnel() {
    val (splitSsids, _, ssids) = commands(StoredControl())
    assertFalse(splitSsids.addExcludedSsid("Home", ""))
    assertTrue(ssids.read("").isEmpty())
  }

  @Test
  fun addExcludedSsidWritesAndDedupes() {
    val (splitSsids, _, ssids) = commands()
    assertTrue(splitSsids.addExcludedSsid("Home", "office"))
    assertFalse(splitSsids.addExcludedSsid("Home", "office"))
    assertEquals(setOf("Home"), ssids.read("office"))
  }

  @Test
  fun setSplitTunnelWritesExcludeMode() {
    val (splitSsids, splits) = commands()
    splitSsids.setSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("com.foo"), "office")
    assertEquals(SplitTunnelMode.EXCLUDE_APPS, splits.read("office").mode)
    assertEquals(setOf("com.foo"), splits.read("office").packages)
  }

  @Test
  fun removeExcludedSsidIgnoresBlankTunnel() {
    val (splitSsids, _, ssids) = commands()
    ssids.write("office", setOf("Home"))
    splitSsids.removeExcludedSsid("Home", "")
    assertEquals(setOf("Home"), ssids.read("office"))
  }

  @Test
  fun removeExcludedSsidDeletes() {
    val (splitSsids, _, ssids) = commands()
    ssids.write("office", setOf("Home"))
    splitSsids.removeExcludedSsid("Home", "office")
    assertEquals(emptySet(), ssids.read("office"))
  }

  @Test
  fun setConnectOnMobileIgnoresBlankAndUnknown() {
    val store = InMemoryControlStore(StoredControl(enabled = true, tunnelName = "office"))
    val catalog = InMemoryTunnelCatalog(mapOf("office" to "x"))
    val splitSsids = SplitSsidCommands(
      store = store,
      applyRunner = ApplyRunner(
        store,
        { now },
        { NetworkSnapshot(NetworkKind.MOBILE) },
        RecordingTunnel(),
      ),
      splitTunnels = InMemorySplitTunnelStore(),
      excludedSsids = InMemoryExcludedSsidStore(),
      catalog = catalog,
      resolver = ControlResolver(store) { now },
    )
    splitSsids.setConnectOnMobile(true, "")
    assertEquals("", store.read().mobileTunnelName)
    splitSsids.setConnectOnMobile(true, "missing")
    assertEquals("", store.read().mobileTunnelName)
    splitSsids.setConnectOnMobile(true, "office")
    assertEquals("office", store.read().mobileTunnelName)
    splitSsids.setConnectOnMobile(false, "office")
    assertEquals("", store.read().mobileTunnelName)
  }
}
