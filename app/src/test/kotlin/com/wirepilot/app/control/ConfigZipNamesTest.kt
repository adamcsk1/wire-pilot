package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigZipNamesTest {
  @Test
  fun readsNameFromConfAndNestedPath() {
    assertEquals("HomeVPN", ConfigZipNames.tunnelNameFromPath("HomeVPN.conf"))
    assertEquals("HomeVPN", ConfigZipNames.tunnelNameFromPath("folder/HomeVPN.conf"))
    assertEquals("HomeVPN", ConfigZipNames.tunnelNameFromPath("folder/HomeVPN.CONF"))
  }

  @Test
  fun rejectsNonConfAndBlank() {
    assertNull(ConfigZipNames.tunnelNameFromPath("notes.txt"))
    assertNull(ConfigZipNames.tunnelNameFromPath("folder/"))
    assertNull(ConfigZipNames.tunnelNameFromPath(".conf"))
    assertEquals("name", ConfigZipNames.tunnelNameFromPath("bad/name.conf"))
  }

  @Test
  fun fileNameAndValidity() {
    assertEquals("office.conf", ConfigZipNames.fileName("office"))
    assertTrue(ConfigZipNames.isValidTunnelName("office"))
    assertTrue(ConfigZipNames.isValidTunnelName("HomeVPN"))
    assertFalse(ConfigZipNames.isValidTunnelName(" "))
    assertFalse(ConfigZipNames.isValidTunnelName("a/b"))
    assertFalse(ConfigZipNames.isValidTunnelName("a\\b"))
    assertFalse(ConfigZipNames.isValidTunnelName("this-name-is-way-too-long"))
  }
}
