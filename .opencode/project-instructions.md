# Wire Pilot Project Instructions

Wire Pilot is a single-purpose Android companion that toggles the official WireGuard app. It does not embed a VPN and must not depend on `com.wireguard.android:tunnel`.

Official control surface:

- Package `com.wireguard.android`
- Permission `com.wireguard.android.permission.CONTROL_TUNNELS`
- Actions `com.wireguard.android.action.SET_TUNNEL_UP` and `SET_TUNNEL_DOWN`
- Extra `tunnel` is the exact tunnel name
- Remote control is off until the user enables WireGuard Settings → Allow remote control apps
- There is no official API to list tunnels or read tunnel state

Policy:

- Control off or pause active: do nothing
- Blank tunnel name: do nothing
- On Wi-Fi with unreadable SSID: do nothing
- On Wi-Fi and SSID is excluded: `SET_TUNNEL_DOWN`
- Every other case, including cellular: `SET_TUNNEL_UP`
- Read SSID from every `TRANSPORT_WIFI` network, never only `activeNetwork`
- Network change and boot wait 3 seconds, then apply once
- Timed pause expiry applies immediately
- No foreground service, no polling, no battery-optimization prompt

## Layout

```text
app/src/main/kotlin/com/wirepilot/app/control/   Pure policy, coordinators, home controller
app/src/main/kotlin/com/wirepilot/app/data/      Store interface and SSID codec
app/src/main/kotlin/com/wirepilot/app/platform/  Android adapters
app/src/main/kotlin/com/wirepilot/app/receiver/  Boot, network, pause receivers
app/src/test/kotlin/                            JVM unit tests
```

Keep decision logic in `control/` and `data/`. Keep Activity, receivers, ConnectivityManager, AlarmManager, and SharedPreferences thin.

## Commands

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat jacocoTestCoverageVerification
./gradlew.bat assembleDebug
```

JaCoCo instruction coverage for `com.wirepilot.app.control` and `com.wirepilot.app.data` must stay at or above 90%.

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
