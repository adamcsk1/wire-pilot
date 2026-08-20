#!/usr/bin/env sh
set -e

if command -v java >/dev/null 2>&1 && [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  java -jar gradle/wrapper/gradle-wrapper.jar "$@"
  exit 0
fi

if command -v gradle >/dev/null 2>&1; then
  gradle "$@"
  exit 0
fi

echo "Gradle is not available. Install Gradle or use Android Studio to run the project."
exit 1
