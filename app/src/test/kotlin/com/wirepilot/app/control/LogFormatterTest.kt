package com.wirepilot.app.control

import com.wirepilot.app.data.LogEvent
import com.wirepilot.app.data.LogKind
import com.wirepilot.app.data.StoredControl
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class LogFormatterTest {
  private val utc = ZoneOffset.UTC

  @Test
  fun formatIncludesTimeKindAndDetail() {
    val line = LogFormatter.format(LogEvent(0L, LogKind.APPLY, "apply=up"), utc)
    assertEquals("00:00:00 APPLY apply=up", line)
  }

  @Test
  fun formatOmitsBlankDetail() {
    assertEquals("00:00:00 BOOT", LogFormatter.format(LogEvent(0L, LogKind.BOOT, ""), utc))
  }

  @Test
  fun previewKeepsLastEntries() {
    val events = listOf(
      LogEvent(0L, LogKind.BOOT, "one"),
      LogEvent(1000L, LogKind.APPLY, "two"),
      LogEvent(2000L, LogKind.APPLY, "three"),
    )
    assertEquals(
      "showing last 2 of 3\n00:00:01 APPLY two\n00:00:02 APPLY three",
      LogFormatter.preview(events, 2, utc),
    )
    assertEquals("showing last 0 of 3", LogFormatter.preview(events, 0, utc))
    assertEquals("", LogFormatter.preview(emptyList(), 80, utc))
  }

  @Test
  fun applyDetailDescribesSkipAndWifi() {
    val detail = LogFormatter.applyDetail(
      trigger = "debounce",
      control = StoredControl(enabled = true, tunnelName = "office"),
      network = NetworkSnapshot(NetworkKind.WIFI, setOf("Cafe", "Home")),
      decision = PolicyDecision.Apply(TunnelCommand.DOWN, "office"),
    )
    assertEquals(
      "trigger=debounce apply=down via=go-backend net=WIFI ssid=${SsidRedactor.redact("Cafe")},${SsidRedactor.redact("Home")} tunnel=office ssidSource=none",
      detail,
    )
  }

  @Test
  fun applyDetailUsesPlaceholders() {
    val detail = LogFormatter.applyDetail(
      trigger = "apply",
      control = StoredControl(enabled = false),
      network = NetworkSnapshot(NetworkKind.WIFI),
      decision = PolicyDecision.Skip(SkipReason.CONTROL_DISABLED),
    )
    assertEquals(
      "trigger=apply apply=skip/CONTROL_DISABLED net=WIFI ssid=? tunnel=(blank) ssidSource=none",
      detail,
    )
  }

  @Test
  fun applyDetailMobile() {
    val detail = LogFormatter.applyDetail(
      trigger = "apply-now",
      control = StoredControl(enabled = true, tunnelName = "office"),
      network = NetworkSnapshot(NetworkKind.MOBILE),
      decision = PolicyDecision.Apply(TunnelCommand.UP, "office"),
    )
    assertEquals(
      "trigger=apply-now apply=up via=go-backend net=MOBILE ssid=- tunnel=office ssidSource=none",
      detail,
    )
  }

  @Test
  fun networkChangeDetail() {
    assertEquals(
      "net=WIFI ssid=${SsidRedactor.redact("Home")} ssidSource=none",
      LogFormatter.networkChangeDetail(NetworkSnapshot(NetworkKind.WIFI, setOf("Home"))),
    )
  }

  @Test
  fun applyOmitsProbeAndRedactsSsid() {
    val network = NetworkSnapshot(NetworkKind.WIFI, setOf("Home"), probe = "nearby=T fine=T locOn=T")
    assertEquals(
      "trigger=apply apply=up via=go-backend net=WIFI ssid=${SsidRedactor.redact("Home")} tunnel=office ssidSource=none",
      LogFormatter.applyDetail(
        trigger = "apply",
        control = StoredControl(enabled = true, tunnelName = "office"),
        network = network,
        decision = PolicyDecision.Apply(TunnelCommand.UP, "office"),
      ),
    )
    assertEquals(
      "net=WIFI ssid=${SsidRedactor.redact("Home")} ssidSource=none",
      LogFormatter.networkChangeDetail(network),
    )
  }
}
