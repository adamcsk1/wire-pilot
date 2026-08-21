package com.wirepilot.app.data

import com.wirepilot.app.data.SplitTunnelMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmptyStoresTest {
  @Test
  fun emptyCatalogIsNoOp() {
    assertTrue(EmptyTunnelCatalog.names().isEmpty())
    assertNull(EmptyTunnelCatalog.readConf("office"))
    EmptyTunnelCatalog.writeConf("office", "[Interface]")
    EmptyTunnelCatalog.delete("office")
    assertTrue(EmptyTunnelCatalog.names().isEmpty())
  }

  @Test
  fun emptySplitStoreIsNoOp() {
    assertEquals(StoredSplitTunnel(), EmptySplitTunnelStore.read("office"))
    EmptySplitTunnelStore.write("office", StoredSplitTunnel(SplitTunnelMode.EXCLUDE_APPS, setOf("a")))
    EmptySplitTunnelStore.delete("office")
    assertEquals(StoredSplitTunnel(), EmptySplitTunnelStore.read("office"))
  }

  @Test
  fun emptyExcludedSsidStoreIsNoOp() {
    assertEquals(emptySet(), EmptyExcludedSsidStore.read("office"))
    EmptyExcludedSsidStore.write("office", setOf("Home"))
    EmptyExcludedSsidStore.delete("office")
    assertEquals(false, EmptyExcludedSsidStore.exists("office"))
    assertEquals(emptySet(), EmptyExcludedSsidStore.read("office"))
  }

  @Test
  fun emptyAppLockStoreIsNoOp() {
    assertEquals(AppLockState(), EmptyAppLockStore.read())
    EmptyAppLockStore.write(AppLockState(enabled = true, pinSalt = "aa", pinHash = "bb"))
    assertEquals(AppLockState(), EmptyAppLockStore.read())
  }
}
