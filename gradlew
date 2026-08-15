#!/usr/bin/env sh
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_PROPERTIES="$PROJECT_DIR/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Missing $WRAPPER_PROPERTIES" >&2
  exit 1
fi

DISTRIBUTION_URL="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES" | head -n 1 | sed 's/\\:/:/g')"
GRADLE_ARCHIVE="$(basename "$DISTRIBUTION_URL")"
GRADLE_VERSION="$(printf '%s' "$GRADLE_ARCHIVE" | sed -E 's/^gradle-(.+)-bin\.zip$/\1/')"

if [ -z "$DISTRIBUTION_URL" ] || [ "$GRADLE_VERSION" = "$GRADLE_ARCHIVE" ]; then
  echo "Unsupported Gradle distributionUrl in $WRAPPER_PROPERTIES" >&2
  exit 1
fi

GRADLE_DIR="$PROJECT_DIR/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_ZIP="$GRADLE_DIR/$GRADLE_ARCHIVE"
GRADLE_EXE="$GRADLE_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_EXE" ]; then
  echo "Gradle $GRADLE_VERSION not found. Downloading..."
  mkdir -p "$GRADLE_DIR"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "$DISTRIBUTION_URL" -o "$GRADLE_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget "$DISTRIBUTION_URL" -O "$GRADLE_ZIP"
  else
    echo "curl or wget is required to download Gradle." >&2
    exit 1
  fi

  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "$GRADLE_ZIP" -d "$GRADLE_DIR"
  else
    echo "unzip is required to unpack Gradle." >&2
    exit 1
  fi
fi

exec "$GRADLE_EXE" "$@"
