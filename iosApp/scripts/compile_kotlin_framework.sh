#!/bin/sh
set -eu

if [ "YES" = "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
  exit 0
fi

JAVA_21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
if [ -z "$JAVA_21_HOME" ]; then
  echo "ERROR: JDK 21 is required for this project but was not found."
  /usr/libexec/java_home -V 2>&1 || true
  exit 1
fi

export JAVA_HOME="$JAVA_21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

if [ -z "${SDK_NAME:-}" ] && [ -n "${PLATFORM_NAME:-}" ]; then
  export SDK_NAME="$PLATFORM_NAME"
fi

if [ -z "${ARCHS:-}" ]; then
  export ARCHS=arm64
fi

if [ -z "${KOTLIN_FRAMEWORK_BUILD_TYPE:-}" ]; then
  case "${CONFIGURATION:-}" in
    *Release*) export KOTLIN_FRAMEWORK_BUILD_TYPE=release ;;
    *) export KOTLIN_FRAMEWORK_BUILD_TYPE=debug ;;
  esac
fi

ENV_FILE="$SRCROOT/../.env"
IOS_SECRETS_FILE="$SRCROOT/Configuration/Secrets.xcconfig"
if [ -f "$ENV_FILE" ]; then
  IOS_KEY=$(awk -F= '/^GOOGLE_MAPS_API_KEY=/{sub(/^[^=]*=/,""); val=$0} END{print val}' "$ENV_FILE" | tr -d '\r')
  BACKEND_BASE_URL=$(awk -F= '/^FIELD_INSIGHTS_BASE_URL=/{sub(/^[^=]*=/,""); val=$0} END{print val}' "$ENV_FILE" | tr -d '\r')

  if [ -n "$IOS_KEY" ] || [ -n "$BACKEND_BASE_URL" ]; then
    : > "$IOS_SECRETS_FILE"
    if [ -n "$IOS_KEY" ]; then
      printf 'GOOGLE_MAPS_API_KEY=%s\n' "$IOS_KEY" >> "$IOS_SECRETS_FILE"
    fi
    if [ -n "$BACKEND_BASE_URL" ]; then
      # xcconfig treats // as comments; rewrite :// to :/$()/ so Xcode reconstructs it.
      SAFE_BACKEND_URL=$(printf '%s' "$BACKEND_BASE_URL" | sed 's#://#:/$()/#')
      printf 'FIELD_INSIGHTS_BASE_URL=%s\n' "$SAFE_BACKEND_URL" >> "$IOS_SECRETS_FILE"
    fi
  else
    rm -f "$IOS_SECRETS_FILE"
  fi
else
  rm -f "$IOS_SECRETS_FILE"
fi

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Xcode env: SDK_NAME=${SDK_NAME:-} ARCHS=${ARCHS:-} CONFIGURATION=${CONFIGURATION:-} KOTLIN_FRAMEWORK_BUILD_TYPE=${KOTLIN_FRAMEWORK_BUILD_TYPE:-}"
java -version

cd "$SRCROOT/.."
./gradlew --no-configuration-cache :composeApp:embedAndSignAppleFrameworkForXcode --stacktrace
