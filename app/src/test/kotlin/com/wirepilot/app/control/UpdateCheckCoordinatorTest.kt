package com.wirepilot.app.control

import com.wirepilot.app.data.GitHubRelease
import com.wirepilot.app.data.StoredUpdateCheck
import com.wirepilot.app.support.InMemoryUpdateCheckStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateCheckCoordinatorTest {
  @Test
  fun rescheduleArmsDayOutWhenNotifyEnabled() {
    val fixture = Fixture()
    fixture.coordinator.reschedule()
    assertEquals(listOf("schedule:${10L + UpdateCheckSchedule.INTERVAL_MS}"), fixture.events)
  }

  @Test
  fun rescheduleCancelsWhenNotifyDisabled() {
    val fixture = Fixture(StoredUpdateCheck(notifyEnabled = false))
    fixture.coordinator.reschedule()
    assertEquals(listOf("cancel"), fixture.events)
  }

  @Test
  fun setNotifyEnabledPersistsAndReschedules() {
    val fixture = Fixture()
    fixture.coordinator.setNotifyEnabled(false)
    assertEquals(false, fixture.store.read().notifyEnabled)
    assertEquals(listOf("cancel"), fixture.events)
  }

  @Test
  fun checkNowNotifiesNewerRelease() {
    val fixture = Fixture()
    val decision = fixture.coordinator.checkNow()
    assertTrue(decision is UpdateCheckDecision.Available)
    assertEquals("1.0.1", fixture.store.read().lastNotifiedTag)
    assertEquals(10L, fixture.store.read().lastCheckEpochMillis)
    assertEquals(
      listOf("schedule:${10L + UpdateCheckSchedule.INTERVAL_MS}", "notify:1.0.1"),
      fixture.events,
    )
  }

  @Test
  fun checkNowDoesNotNotifyWhenDisabled() {
    val fixture = Fixture(StoredUpdateCheck(notifyEnabled = false))
    val decision = fixture.coordinator.checkNow()
    assertTrue(decision is UpdateCheckDecision.Available)
    assertEquals("", fixture.store.read().lastNotifiedTag)
    assertEquals(listOf("cancel"), fixture.events)
  }

  @Test
  fun checkNowUpToDateStampsLastCheck() {
    val fixture = Fixture(remoteTag = "1.0.0")
    assertEquals(UpdateCheckDecision.UpToDate, fixture.coordinator.checkNow())
    assertEquals(10L, fixture.store.read().lastCheckEpochMillis)
    assertEquals("", fixture.store.read().lastNotifiedTag)
  }

  @Test
  fun periodicSkipsWhenNotDue() {
    val lastCheck = 5L
    val fixture = Fixture(StoredUpdateCheck(lastCheckEpochMillis = lastCheck), now = lastCheck + 10L)
    fixture.coordinator.onPeriodicCheck()
    assertEquals(
      listOf("schedule:${lastCheck + UpdateCheckSchedule.INTERVAL_MS}"),
      fixture.events,
    )
    assertEquals(lastCheck, fixture.store.read().lastCheckEpochMillis)
  }

  @Test
  fun periodicNotifiesWhenDue() {
    val lastCheck = 1L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS
    val fixture = Fixture(StoredUpdateCheck(lastCheckEpochMillis = lastCheck), now = now)
    fixture.coordinator.onPeriodicCheck()
    assertEquals("1.0.1", fixture.store.read().lastNotifiedTag)
    assertEquals("schedule:${now + UpdateCheckSchedule.INTERVAL_MS}", fixture.events.first())
    assertTrue(fixture.events.contains("notify:1.0.1"))
  }

  @Test
  fun periodicArmsNextAlarmBeforeFetch() {
    val lastCheck = 1L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS
    val events = mutableListOf<String>()
    val store = InMemoryUpdateCheckStore(StoredUpdateCheck(lastCheckEpochMillis = lastCheck))
    var fetchSawSchedule = false
    val coordinator = UpdateCheckCoordinator(
      store = store,
      clock = { now },
      installedVersionName = { "1.0.0" },
      fetchLatest = {
        fetchSawSchedule = events.any { event -> event.startsWith("schedule:") }
        GitHubReleaseFetch.Failed
      },
      scheduleAlarm = { atEpochMillis -> events += "schedule:$atEpochMillis" },
      cancelAlarm = { events += "cancel" },
      showNotification = { tagName, _ ->
        events += "notify:$tagName"
        true
      },
    )
    coordinator.onPeriodicCheck()
    assertTrue(fetchSawSchedule)
    assertEquals(now, store.read().lastCheckEpochMillis)
    assertEquals("schedule:${now + UpdateCheckSchedule.INTERVAL_MS}", events.first())
  }

  @Test
  fun periodicStampsLastCheckBeforeFetchReturns() {
    val lastCheck = 1L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS
    val store = InMemoryUpdateCheckStore(StoredUpdateCheck(lastCheckEpochMillis = lastCheck))
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val coordinator = UpdateCheckCoordinator(
      store = store,
      clock = { now },
      installedVersionName = { "1.0.0" },
      fetchLatest = {
        assertEquals(now, store.read().lastCheckEpochMillis)
        started.countDown()
        release.await()
        GitHubReleaseFetch.Failed
      },
      scheduleAlarm = {},
      cancelAlarm = {},
      showNotification = { _, _ -> true },
    )
    val worker = Thread { coordinator.onPeriodicCheck() }
    worker.start()
    assertTrue(started.await(1, TimeUnit.SECONDS))
    assertEquals(now, store.read().lastCheckEpochMillis)
    release.countDown()
    worker.join()
  }

  @Test
  fun persistKeepsNotifyToggleMadeDuringFetch() {
    val store = InMemoryUpdateCheckStore()
    val events = mutableListOf<String>()
    lateinit var coordinator: UpdateCheckCoordinator
    coordinator = UpdateCheckCoordinator(
      store = store,
      clock = { 10L },
      installedVersionName = { "1.0.0" },
      fetchLatest = {
        coordinator.setNotifyEnabled(false)
        GitHubReleaseFetch.Ok(
          GitHubRelease(
            tagName = "1.0.1",
            htmlUrl = "https://github.com/adamcsk1/wire-pilot/releases/tag/1.0.1",
          ),
        )
      },
      scheduleAlarm = { atEpochMillis -> events += "schedule:$atEpochMillis" },
      cancelAlarm = { events += "cancel" },
      showNotification = { tagName, _ ->
        events += "notify:$tagName"
        true
      },
    )
    val decision = coordinator.checkNow()
    assertTrue(decision is UpdateCheckDecision.Available)
    assertEquals(false, (decision as UpdateCheckDecision.Available).notify)
    assertEquals(false, store.read().notifyEnabled)
    assertEquals(10L, store.read().lastCheckEpochMillis)
    assertEquals("", store.read().lastNotifiedTag)
    assertTrue(events.none { event -> event.startsWith("notify:") })
  }

  @Test
  fun periodicAlreadyNotifiedDoesNotNotifyAgain() {
    val lastCheck = 1L
    val now = lastCheck + UpdateCheckSchedule.INTERVAL_MS
    val fixture = Fixture(
      StoredUpdateCheck(lastCheckEpochMillis = lastCheck, lastNotifiedTag = "1.0.1"),
      now = now,
    )
    fixture.coordinator.onPeriodicCheck()
    assertTrue(fixture.events.none { event -> event.startsWith("notify:") })
  }

  @Test
  fun periodicCancelsWhenDisabled() {
    val fixture = Fixture(StoredUpdateCheck(notifyEnabled = false))
    fixture.coordinator.onPeriodicCheck()
    assertEquals(listOf("cancel"), fixture.events)
  }

  @Test
  fun failedFetchDoesNotNotify() {
    val fixture = Fixture(fetch = GitHubReleaseFetch.Failed)
    assertEquals(UpdateCheckDecision.Failed, fixture.coordinator.checkNow())
    assertTrue(fixture.events.none { event -> event.startsWith("notify:") })
  }

  @Test
  fun doesNotStampLastNotifiedTagWhenNotifyFails() {
    val fixture = Fixture(notifySucceeds = false)
    val decision = fixture.coordinator.checkNow()
    assertTrue(decision is UpdateCheckDecision.Available)
    assertEquals(true, (decision as UpdateCheckDecision.Available).notify)
    assertEquals("", fixture.store.read().lastNotifiedTag)
    assertEquals(10L, fixture.store.read().lastCheckEpochMillis)
    assertTrue(fixture.events.contains("notify:1.0.1"))
  }

  private class Fixture(
    initial: StoredUpdateCheck = StoredUpdateCheck(),
    remoteTag: String = "1.0.1",
    fetch: GitHubReleaseFetch = GitHubReleaseFetch.Ok(
      GitHubRelease(
        tagName = remoteTag,
        htmlUrl = "https://github.com/adamcsk1/wire-pilot/releases/tag/$remoteTag",
      ),
    ),
    now: Long = 10L,
    notifySucceeds: Boolean = true,
  ) {
    val store = InMemoryUpdateCheckStore(initial)
    val events = mutableListOf<String>()
    val coordinator = UpdateCheckCoordinator(
      store = store,
      clock = { now },
      installedVersionName = { "1.0.0" },
      fetchLatest = { fetch },
      scheduleAlarm = { atEpochMillis -> events += "schedule:$atEpochMillis" },
      cancelAlarm = { events += "cancel" },
      showNotification = { tagName, _ ->
        events += "notify:$tagName"
        notifySucceeds
      },
    )
  }
}
