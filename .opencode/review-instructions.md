# PR Review Instructions - Wire Pilot

You are reviewing pull requests for Wire Pilot, a single-purpose Android companion that controls the official WireGuard app through its documented broadcast API.

Review for correctness, security, maintainability, test coverage, and project convention violations. Do not comment on formatting, whitespace, or import ordering unless the issue changes runtime behavior.

## Review Output

- Report only actionable findings.
- Order findings by severity: `critical`, `high`, `medium`, then `low`.
- Include the affected file and line range whenever possible.
- Explain why the issue matters and what should change.
- Do not praise the PR or summarize unchanged code.
- If there are no meaningful findings, respond with `LGTM` and mention any residual testing risk.

Use this format for findings:

```md
severity: file:line
Issue description and why it matters. Suggested fix.
```

## Must Review

- Policy, debounce, boot, and pause behavior.
- Official WireGuard intent package, action, extra, and permission usage.
- SSID reading across all Wi-Fi networks, not only the active network.
- Battery and process lifetime: no foreground service, no polling, no battery-optimization prompt.
- Android JVM tests and JaCoCo gate for `control` and `data`.
- Gradle, manifest, and permission changes.

## Android

- Pure Kotlin helpers belong in `control/` and `data/` so local JVM tests can cover them.
- Activity, receivers, and SDK wrappers stay thin.
- AGP 9+ provides Kotlin support directly; do not require `org.jetbrains.kotlin.android` when AGP rejects it.
- Avoid committing `local.properties`, `.idea`, `.gradle`, or hardcoded `org.gradle.java.home`.
- Instruction coverage for `com.wirepilot.app.control` and `com.wirepilot.app.data` must remain at or above 90%.

## Testing

- Every changed source file in `control/` or `data/` must have its spec updated.
- New branches require new test cases.
- When tests fail, diagnose the root cause first. Fix production code when the failure is a real defect.
