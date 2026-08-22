# WirePilot Project Instructions

WirePilot is a single-purpose Android VPN that embeds official `com.wireguard.android:tunnel` and applies SSID rules. It does not control the official WireGuard app.

Tunnel surface:

- `GoBackend` + `GoBackend.VpnService`
- Import/export official ZIP of `*.conf` (name = filename minus `.conf`) and single `.conf`
- Many tunnels, one active
- Split tunnel: `AllowedIPs` from the conf plus per-app exclude XOR include
- Always-on via `VpnService.prepare()` and `GoBackend.setAlwaysOnCallback`

Policy:

- Control off or pause active: `DOWN` if a tunnel is selected
- Blank / no imported tunnel: do nothing
- On Wi-Fi with unreadable SSID: do nothing (unless last-known SSID is within 60s)
- On Wi-Fi and SSID is excluded: `DOWN`
- Cellular / other: `UP` the designated mobile tunnel, or `DOWN` if none
- Off/Pause: `DOWN` the default tunnel and the mobile tunnel if they differ
- Every other Wi-Fi case: `UP` the default tunnel
- Read SSID from every `TRANSPORT_WIFI` network, never only `activeNetwork`
- Network change and boot wait 3 seconds, then apply once
- Timed pause expiry applies immediately
- A special-use|location foreground service keeps event-driven network monitoring alive while control is enabled or timed-paused so SSID stays readable without opening the UI
- No polling or battery-optimization prompt

## Layout

```text
app/src/main/kotlin/com/wirepilot/app/control/   Pure policy, coordinators, home controller
app/src/main/kotlin/com/wirepilot/app/data/      Store interface and codecs
app/src/main/kotlin/com/wirepilot/app/platform/  Android adapters, VPN, ZIP IO
app/src/main/kotlin/com/wirepilot/app/receiver/  Boot, network, pause receivers
app/src/test/kotlin/                            JVM unit tests
```

Keep decision logic in `control/` and `data/`. Keep Activity, receivers, ConnectivityManager, AlarmManager, and SharedPreferences thin. Never persist plaintext private keys. Never `Log.d` policy, SSID, or probe details. Never log private keys.

## Commands

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat jacocoTestCoverageVerification
./gradlew.bat assembleDebug
```

JaCoCo instruction coverage for `com.wirepilot.app.control` and `com.wirepilot.app.data` must stay at or above 95%.

Android Gradle commands require Java 17 or newer. The project uses AGP 9+, which provides Kotlin support directly; do not add `org.jetbrains.kotlin.android` unless the Android Gradle plugin version requires it.

## Working In This Codebase

Always read the relevant source files before making changes. Do not suggest or apply modifications based on assumptions.

When changing a source file, update its spec file when behavior, dependencies, imports, branches, or assertions change. Add cases for new code paths.

When tests fail, diagnose the root cause first. If the failure exposes a bug in production code, fix production code. Only update the test when production code is correct and the test is genuinely out of date.

Parameter names must be descriptive. Do not use single-letter or abbreviated names except `a`/`b` in sort comparators.

## Commit Conventions

Format: `type(scope): short imperative summary`.

- Use imperative mood.
- Lowercase after the colon.
- No trailing period.
- Keep the summary at or under 72 characters.
- Body explains why, not what.

Types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `style`, `perf`.

Scopes: `control`, `ui`, `platform`, `receiver`, `build`, `test`, `opencode`.
