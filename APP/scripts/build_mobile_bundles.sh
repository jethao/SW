#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android"
IOS_DIR="$ROOT_DIR/ios"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"
APP_INIT_ENV_FILE="$ROOT_DIR/.app-init.env"
IOS_DERIVED_DATA="$ARTIFACTS_DIR/ios/DerivedData"
IOS_ARCHIVES_DIR="$ARTIFACTS_DIR/ios/archives"
IOS_EXPORT_DIR="$ARTIFACTS_DIR/ios/export"
ANDROID_ARTIFACTS_DIR="$ARTIFACTS_DIR/android"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$ARTIFACTS_DIR/gradle-home}"

usage() {
  cat <<'EOF'
Usage:
  build_mobile_bundles.sh android-debug
  build_mobile_bundles.sh android-release-apk
  build_mobile_bundles.sh android-release-bundle
  build_mobile_bundles.sh ios-simulator
  build_mobile_bundles.sh ios-archive
  build_mobile_bundles.sh ios-export

Android release signing:
  Provide signing values either through android/keystore.properties
  or these environment variables:
    AIRHEALTH_ANDROID_KEYSTORE_PATH
    AIRHEALTH_ANDROID_STORE_PASSWORD
    AIRHEALTH_ANDROID_KEY_ALIAS
    AIRHEALTH_ANDROID_KEY_PASSWORD

iOS archive/export:
  Required environment variables:
    AIRHEALTH_IOS_DEVELOPMENT_TEAM
  Optional overrides:
    AIRHEALTH_IOS_BUNDLE_ID
    AIRHEALTH_IOS_EXPORT_METHOD   (default: development)
    AIRHEALTH_IOS_ARCHIVE_PATH
    AIRHEALTH_IOS_EXPORT_PATH
EOF
}

ensure_dir() {
  mkdir -p "$1"
}

ensure_android_toolchain() {
  if [[ -f "$APP_INIT_ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$APP_INIT_ENV_FILE"
  fi

  export GRADLE_USER_HOME="$GRADLE_HOME_DIR"
  ensure_dir "$GRADLE_USER_HOME"
  export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ANDROID_ARTIFACTS_DIR/.android}"
  ensure_dir "$ANDROID_USER_HOME"

  if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x /usr/libexec/java_home ]]; then
      local detected_java_home
      detected_java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
      if [[ -n "$detected_java_home" ]]; then
        export JAVA_HOME="$detected_java_home"
      fi
    fi

    if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home ]]; then
      export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    fi
  fi

  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

ensure_ios_project() {
  if [[ ! -f "$IOS_DIR/AirHealthApp.xcodeproj/project.pbxproj" ]]; then
    if ! command -v xcodegen >/dev/null 2>&1; then
      echo "xcodegen is required to generate the iOS project." >&2
      exit 1
    fi
    xcodegen generate --spec "$IOS_DIR/project.yml" >/dev/null
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

android_debug() {
  ensure_android_toolchain
  ensure_dir "$ANDROID_ARTIFACTS_DIR"
  gradle -p "$ANDROID_DIR" assembleDebug
  echo "APK output: $ANDROID_DIR/app/build/outputs/apk/debug"
}

android_release_apk() {
  ensure_android_toolchain
  ensure_dir "$ANDROID_ARTIFACTS_DIR"
  gradle -p "$ANDROID_DIR" assembleRelease
  echo "APK output: $ANDROID_DIR/app/build/outputs/apk/release"
}

android_release_bundle() {
  ensure_android_toolchain
  ensure_dir "$ANDROID_ARTIFACTS_DIR"
  gradle -p "$ANDROID_DIR" bundleRelease
  echo "AAB output: $ANDROID_DIR/app/build/outputs/bundle/release"
}

ios_simulator() {
  ensure_ios_project
  ensure_dir "$IOS_DERIVED_DATA"
  xcodebuild \
    -project "$IOS_DIR/AirHealthApp.xcodeproj" \
    -scheme AirHealthApp \
    -sdk iphonesimulator \
    -derivedDataPath "$IOS_DERIVED_DATA" \
    build
  echo "App output: $IOS_DERIVED_DATA/Build/Products/Debug-iphonesimulator/AirHealthApp.app"
}

ios_archive() {
  ensure_ios_project
  require_env AIRHEALTH_IOS_DEVELOPMENT_TEAM
  ensure_dir "$IOS_DERIVED_DATA"
  ensure_dir "$IOS_ARCHIVES_DIR"

  local archive_path="${AIRHEALTH_IOS_ARCHIVE_PATH:-$IOS_ARCHIVES_DIR/AirHealthApp.xcarchive}"
  local bundle_id="${AIRHEALTH_IOS_BUNDLE_ID:-com.airhealth.app}"

  xcodebuild \
    -project "$IOS_DIR/AirHealthApp.xcodeproj" \
    -scheme AirHealthApp \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$IOS_DERIVED_DATA" \
    DEVELOPMENT_TEAM="$AIRHEALTH_IOS_DEVELOPMENT_TEAM" \
    PRODUCT_BUNDLE_IDENTIFIER="$bundle_id" \
    archive \
    -archivePath "$archive_path"

  echo "Archive output: $archive_path"
}

ios_export() {
  ensure_ios_project
  require_env AIRHEALTH_IOS_DEVELOPMENT_TEAM
  ensure_dir "$IOS_EXPORT_DIR"

  local archive_path="${AIRHEALTH_IOS_ARCHIVE_PATH:-$IOS_ARCHIVES_DIR/AirHealthApp.xcarchive}"
  local export_path="${AIRHEALTH_IOS_EXPORT_PATH:-$IOS_EXPORT_DIR}"
  local export_method="${AIRHEALTH_IOS_EXPORT_METHOD:-development}"
  local export_options="$IOS_EXPORT_DIR/ExportOptions.generated.plist"

  if [[ ! -d "$archive_path" ]]; then
    echo "Archive not found at $archive_path. Run ios-archive first." >&2
    exit 1
  fi

  cat >"$export_options" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>$export_method</string>
    <key>signingStyle</key>
    <string>automatic</string>
    <key>stripSwiftSymbols</key>
    <true/>
    <key>teamID</key>
    <string>$AIRHEALTH_IOS_DEVELOPMENT_TEAM</string>
    <key>thinning</key>
    <string>&lt;none&gt;</string>
</dict>
</plist>
EOF

  xcodebuild \
    -exportArchive \
    -archivePath "$archive_path" \
    -exportPath "$export_path" \
    -exportOptionsPlist "$export_options"

  echo "Export output: $export_path"
}

main() {
  ensure_dir "$ARTIFACTS_DIR"

  case "${1:-}" in
    android-debug) android_debug ;;
    android-release-apk) android_release_apk ;;
    android-release-bundle) android_release_bundle ;;
    ios-simulator) ios_simulator ;;
    ios-archive) ios_archive ;;
    ios-export) ios_export ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
