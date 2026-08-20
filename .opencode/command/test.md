---
description: Run WirePilot JVM unit tests and the 95% JaCoCo coverage gate.
agent: testing
---

Run `./gradlew.bat testDebugUnitTest jacocoTestCoverageVerification` from the repo root. Report failing tests and the coverage ratio. If coverage is below 95% for `control` or `data`, identify uncovered branches and add tests.
