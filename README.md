# WirePilot

WirePilot is a single-purpose Android VPN. It embeds the official [`com.wireguard.android:tunnel`](https://git.zx2c4.com/wireguard-android) library and turns the tunnel up or down from Wi‑Fi SSID rules.

It does **not** talk to the official WireGuard app.

## Requirements

- Android 15 or newer
- VPN permission when connecting
- Location (on and allowed) plus Nearby Wi‑Fi so policy can read the network name
- Java 17 or newer to build

## Features

- Import and export official WireGuard ZIP archives of `*.conf` files, or a single `.conf`
- Create and edit tunnels in the app
- Many tunnels, one active
- Default tunnel for Wi‑Fi, plus one designated mobile-data tunnel
- Skip VPN on chosen SSIDs
- Split tunnel: `AllowedIPs` from the config, plus per-app exclude **or** include
- Automatic control: on, off, or timed pause (1–24 hours)
- Optional always-on VPN (the OS may restart the VPN; WirePilot still decides up vs down)
- Optional app lock (PIN and biometrics)
- Optional local logs and live tunnel usage

## How control works

When automatic control is **off** or **paused**, WirePilot takes the default tunnel and the mobile tunnel down if they differ.

When control is **on**:

- No imported / selected tunnel: do nothing
- Wi‑Fi with an unreadable SSID: do nothing, unless a last-known SSID is still valid (60 seconds)
- Wi‑Fi on an excluded SSID: tunnel down
- Cellular / other: bring up the designated mobile tunnel, or down if none is set
- Every other Wi‑Fi case: bring up the default tunnel

Network changes and boot wait 3 seconds, then apply once. Timed pause expiry applies immediately.

While control is enabled or timed-paused, a foreground service keeps event-driven network monitoring alive so the SSID stays readable without opening the UI. WirePilot does not poll and does not prompt for battery optimization.

## Build

```bash
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat jacocoTestCoverageVerification
```

Instruction coverage for `com.wirepilot.app.control` and `com.wirepilot.app.data` must stay at or above 95%.

## Security

Private keys are WireGuard secrets. Keep exported ZIP files private — they contain private keys. Do not save them to Drive, email, or a shared folder.

## License

[MIT](LICENSE)
