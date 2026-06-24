#!/usr/bin/env sh
set -e

GRADLE_VERSION="9.5.1"
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_DIR="$PROJECT_DIR/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_ZIP="$GRADLE_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_EXE="$GRADLE_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_EXE" ]; then
  echo "Gradle $GRADLE_VERSION not found. Downloading..."
  mkdir -p "$GRADLE_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -L "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GRADLE_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$GRADLE_ZIP"
  else
    echo "curl or wget is required to download Gradle." >&2
    exit 1
  fi
  unzip -o "$GRADLE_ZIP" -d "$GRADLE_DIR"
fi

exec "$GRADLE_EXE" "$@"
