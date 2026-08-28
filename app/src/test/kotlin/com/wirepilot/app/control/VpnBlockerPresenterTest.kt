package com.wirepilot.app.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VpnBlockerPresenterTest {
  @Test
  fun connectedHidesBlocker() {
    assertNull(
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.WIFI_UP,
        vpnConnected = true,
        consentGranted = false,
        otherVpnActive = true,
      ),
    )
  }

  @Test
  fun missingConsentWhenWifiUp() {
    assertEquals(
      VpnBlocker.CONSENT_MISSING,
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.WIFI_UP,
        vpnConnected = false,
        consentGranted = false,
        otherVpnActive = false,
      ),
    )
  }

  @Test
  fun missingConsentWhenLastKnownWifiUp() {
    assertEquals(
      VpnBlocker.CONSENT_MISSING,
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.WIFI_UP_LAST_KNOWN,
        vpnConnected = false,
        consentGranted = false,
        otherVpnActive = true,
      ),
    )
  }

  @Test
  fun otherVpnWhenMobileUp() {
    assertEquals(
      VpnBlocker.OTHER_VPN,
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.MOBILE_UP,
        vpnConnected = false,
        consentGranted = true,
        otherVpnActive = true,
      ),
    )
  }

  @Test
  fun consentWinsOverOtherVpn() {
    assertEquals(
      VpnBlocker.CONSENT_MISSING,
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.WIFI_UP,
        vpnConnected = false,
        consentGranted = false,
        otherVpnActive = true,
      ),
    )
  }

  @Test
  fun noWarningWhenStartFailedWithoutOtherVpn() {
    assertNull(
      VpnBlockerPresenter.present(
        policyKind = PolicyLineKind.WIFI_UP,
        vpnConnected = false,
        consentGranted = true,
        otherVpnActive = false,
      ),
    )
  }

  @Test
  fun downKindsNeverWarn() {
    val downKinds = PolicyLineKind.entries.filterNot(VpnBlockerPresenter::wantsUp)
    downKinds.forEach { kind ->
      assertNull(
        VpnBlockerPresenter.present(
          policyKind = kind,
          vpnConnected = false,
          consentGranted = false,
          otherVpnActive = true,
        ),
        kind.name,
      )
    }
  }

  @Test
  fun wantsUpKinds() {
    assertEquals(
      setOf(
        PolicyLineKind.WIFI_UP,
        PolicyLineKind.WIFI_UP_LAST_KNOWN,
        PolicyLineKind.MOBILE_UP,
      ),
      PolicyLineKind.entries.filter(VpnBlockerPresenter::wantsUp).toSet(),
    )
  }
}
