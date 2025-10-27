#!/usr/bin/env sh
# Minimal gradlew wrapper that tries to use ./gradle/wrapper/gradle-wrapper.jar if present,
# otherwise falls back to system gradle if installed.
if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
  java -jar "./gradle/wrapper/gradle-wrapper.jar" "$@"
else
  if command -v gradle >/dev/null 2>&1; then
    gradle "$@"
  else
    echo "No gradle wrapper found and 'gradle' not installed. Please install gradle or include wrapper jar."
    exit 1
  fi
fi