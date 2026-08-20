package com.wirepilot.app.data

import com.wirepilot.app.control.SplitTunnelMode
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
    assertEquals(StoredSplitTunnel(), EmptySplitTunnelStore.read("office"))
  }
}
