#!/usr/bin/env bash

set -u -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

find_airhealth_root() {
  local start_dir="$1"
  local current="${start_dir}"

  while [ "${current}" != "/" ]; do
    if [ -d "${current}/SW/APP" ] && [ -d "${current}/SW/feature-design" ] && [ -d "${current}/SW/Architecture" ]; then
      printf '%s\n' "${current}"
      return 0
    fi
    current="$(dirname "${current}")"
  done

  return 1
}

REPO_ROOT=""
if [ -n "${AIRHEALTH_REPO_ROOT:-}" ] && [ -d "${AIRHEALTH_REPO_ROOT}/SW/APP" ] && [ -d "${AIRHEALTH_REPO_ROOT}/SW/feature-design" ]; then
  REPO_ROOT="${AIRHEALTH_REPO_ROOT}"
elif REPO_ROOT="$(find_airhealth_root "$(pwd)")"; then
  :
elif REPO_ROOT="$(find_airhealth_root "${SKILL_DIR}")"; then
  :
else
  REPO_ROOT="$(cd "${SKILL_DIR}/../../../.." && pwd)"
fi

APP_ROOT="${REPO_ROOT}/SW/APP"
REPORT_PATH="${APP_ROOT}/initialize.rpt"
BOOTSTRAP_ENV_PATH="${APP_ROOT}/.app-init.env"
PRD_PATH="${REPO_ROOT}/PM/PRD/PRD.md"
MOBILE_DESIGN_DIR="${REPO_ROOT}/SW/feature-design"
ARCH_SPEC_DIR="${REPO_ROOT}/SW/Architecture"
IOS_ARTIFACT_DIR="${APP_ROOT}/artifacts/ios"
ANDROID_ARTIFACT_DIR="${APP_ROOT}/artifacts/android"
DEFAULT_TOOLING_VENV="${APP_ROOT}/.venv-app-init"

STATUS="PARTIAL"
ERROR_MESSAGE=""
ACTIONS_TAKEN=()
REMAINING_GAPS=()

PRD_USED="missing"
LATEST_MOBILE_DESIGN=""
LATEST_ARCH_SPEC=""
MOBILE_BASELINE="unknown"
FRAMEWORK_HINT="unspecified"
IOS_PACKAGE_READINESS="partial"
ANDROID_PACKAGE_READINESS="partial"
SCAFFOLD_STATUS="missing"
SCAFFOLD_KIND="none"
PODFILE_PATH=""
GEMFILE_PATH=""
GRADLEW_PATH=""
PACKAGE_JSON_PATH=""
APP_JSON_PATH=""
EXPO_JSON_PATH=""
XCODEPROJ_PATH=""
ANDROID_SETTINGS_PATH=""
ANDROID_APP_BUILD_PATH=""

NO_INSTALL="${APP_INIT_NO_INSTALL:-0}"
ALLOW_BREW_INSTALL="${AIRHEALTH_APP_ALLOW_BREW_INSTALL:-0}"
CREATE_ARTIFACT_DIRS="${AIRHEALTH_APP_CREATE_ARTIFACT_DIRS:-1}"
CREATE_SCAFFOLD="${AIRHEALTH_APP_CREATE_SCAFFOLD:-1}"
ANDROID_SDK_ROOT_CANDIDATE="${AIRHEALTH_APP_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
TOOLING_VENV="${AIRHEALTH_APP_TOOLING_VENV:-${DEFAULT_TOOLING_VENV}}"

PYTHON3_BIN=""
XCODEBUILD_BIN=""
XCRUN_BIN=""
SWIFT_BIN=""
POD_BIN=""
BUNDLE_BIN=""
JAVA_BIN=""
JAVAC_BIN=""
SDKMANAGER_BIN=""
ADB_BIN=""
EMULATOR_BIN=""
ZIPALIGN_BIN=""
APKSIGNER_BIN=""
GRADLE_BIN=""
BUNDLETOOL_BIN=""
NODE_BIN=""
NPM_BIN=""
YARN_BIN=""
PNPM_BIN=""

XCODE_PATH=""
ANDROID_SDK_ROOT_FOUND=""
TOOLING_VENV_STATUS="missing"
JAVA_HOME_FOUND=""

have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

record_action() {
  ACTIONS_TAKEN+=("$1")
}

record_gap() {
  REMAINING_GAPS+=("$1")
}

join_lines() {
  local prefix="$1"
  shift || true
  if [ "$#" -eq 0 ]; then
    printf "%s none\n" "${prefix}"
    return
  fi
  local item
  for item in "$@"; do
    printf "%s %s\n" "${prefix}" "${item}"
  done
}

find_first_file() {
  local search_root="$1"
  local search_name="$2"
  find "${search_root}" -maxdepth 4 -type f -name "${search_name}" | sort | head -n 1
}

java_cmd_works() {
  local java_bin="$1"
  [ -n "${java_bin}" ] && [ -x "${java_bin}" ] && "${java_bin}" -version >/dev/null 2>&1
}

javac_cmd_works() {
  local javac_bin="$1"
  [ -n "${javac_bin}" ] && [ -x "${javac_bin}" ] && "${javac_bin}" -version >/dev/null 2>&1
}

detect_java_home() {
  if [ -x "/opt/homebrew/opt/openjdk/bin/java" ] && [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    JAVA_HOME_FOUND="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    return 0
  fi

  if [ -n "${JAVA_BIN}" ]; then
    JAVA_HOME_FOUND="$(cd "$(dirname "${JAVA_BIN}")/.." 2>/dev/null && pwd)"
  fi
}

detect_latest_docs() {
  if [ -f "${PRD_PATH}" ]; then
    PRD_USED="${PRD_PATH}"
  fi

  LATEST_MOBILE_DESIGN="$(find "${MOBILE_DESIGN_DIR}" -maxdepth 1 -type f -name 'Mobile_Feature_Design_*.md' | sort -V | tail -n 1)"
  if [ -z "${LATEST_MOBILE_DESIGN}" ]; then
    STATUS="BLOCKED"
    ERROR_MESSAGE="No mobile feature design was found under ${MOBILE_DESIGN_DIR}."
    return 1
  fi

  LATEST_ARCH_SPEC="$(find "${ARCH_SPEC_DIR}" -maxdepth 1 -type f -name 'Software_Architecture_Spec_*.md' | sort -V | tail -n 1)"
  if [ -z "${LATEST_ARCH_SPEC}" ]; then
    STATUS="BLOCKED"
    ERROR_MESSAGE="No software architecture spec was found under ${ARCH_SPEC_DIR}."
    return 1
  fi

  return 0
}

detect_project_files() {
  local detected_kind="none"

  PACKAGE_JSON_PATH="$(find_first_file "${APP_ROOT}" 'package.json')"
  APP_JSON_PATH="$(find_first_file "${APP_ROOT}" 'app.json')"
  EXPO_JSON_PATH="$(find_first_file "${APP_ROOT}" 'expo.json')"
  PODFILE_PATH="$(find_first_file "${APP_ROOT}" 'Podfile')"
  GEMFILE_PATH="$(find_first_file "${APP_ROOT}" 'Gemfile')"
  XCODEPROJ_PATH="$(find "${APP_ROOT}" -maxdepth 4 -type d -name '*.xcodeproj' | sort | head -n 1)"
  GRADLEW_PATH="$(find "${APP_ROOT}" -maxdepth 4 -type f -name 'gradlew' -perm -111 | sort | head -n 1)"
  ANDROID_SETTINGS_PATH="$(find_first_file "${APP_ROOT}" 'settings.gradle.kts')"
  if [ -z "${ANDROID_SETTINGS_PATH}" ]; then
    ANDROID_SETTINGS_PATH="$(find_first_file "${APP_ROOT}" 'settings.gradle')"
  fi
  ANDROID_APP_BUILD_PATH="$(find_first_file "${APP_ROOT}" 'build.gradle.kts')"
  if [ -z "${ANDROID_APP_BUILD_PATH}" ]; then
    ANDROID_APP_BUILD_PATH="$(find_first_file "${APP_ROOT}" 'build.gradle')"
  fi

  if [ -n "${PACKAGE_JSON_PATH}" ] || [ -n "${APP_JSON_PATH}" ] || [ -n "${EXPO_JSON_PATH}" ]; then
    detected_kind="js-based mobile scaffold"
  elif [ -n "${XCODEPROJ_PATH}" ]; then
    detected_kind="native iOS project scaffold"
  elif [ -n "${PODFILE_PATH}" ] || [ -n "${ANDROID_SETTINGS_PATH}" ] || [ -n "${GRADLEW_PATH}" ] || [ -n "${ANDROID_APP_BUILD_PATH}" ]; then
    detected_kind="native baseline scaffold"
  else
    SCAFFOLD_STATUS="missing"
    SCAFFOLD_KIND="none"
    return 0
  fi

  if [ "${SCAFFOLD_STATUS}" != "created" ]; then
    SCAFFOLD_STATUS="reused"
  fi
  SCAFFOLD_KIND="${detected_kind}"
}

infer_mobile_baseline() {
  if grep -Eqi 'iOS 26\+|Android 16\+' "${LATEST_MOBILE_DESIGN}" "${LATEST_ARCH_SPEC}" 2>/dev/null; then
    MOBILE_BASELINE="iOS 26+ and Android 16+"
  else
    MOBILE_BASELINE="mobile baseline not explicitly versioned"
  fi

  if [ -n "${PACKAGE_JSON_PATH}" ] || [ -n "${APP_JSON_PATH}" ] || [ -n "${EXPO_JSON_PATH}" ]; then
    FRAMEWORK_HINT="js-based mobile stack present"
  elif [ -n "${XCODEPROJ_PATH}" ] || [ -n "${PODFILE_PATH}" ] || find "${APP_ROOT}" -maxdepth 3 \( -name '*.xcworkspace' \) | grep -q .; then
    FRAMEWORK_HINT="native iOS scaffolding present"
  elif [ -n "${ANDROID_SETTINGS_PATH}" ] || [ -n "${GRADLEW_PATH}" ] || [ -n "${ANDROID_APP_BUILD_PATH}" ]; then
    FRAMEWORK_HINT="native Android scaffolding present"
  else
    FRAMEWORK_HINT="no framework-specific app scaffold detected"
  fi
}

check_tooling() {
  if have_cmd python3; then
    PYTHON3_BIN="$(command -v python3)"
  else
    record_gap "python3 is missing"
  fi

  if have_cmd xcodebuild; then
    XCODEBUILD_BIN="$(command -v xcodebuild)"
  else
    record_gap "xcodebuild is missing"
  fi

  if have_cmd xcrun; then
    XCRUN_BIN="$(command -v xcrun)"
  else
    record_gap "xcrun is missing"
  fi

  if have_cmd swift; then
    SWIFT_BIN="$(command -v swift)"
  fi

  if have_cmd pod; then
    POD_BIN="$(command -v pod)"
  fi

  if have_cmd bundle; then
    BUNDLE_BIN="$(command -v bundle)"
  fi

  if have_cmd java && java_cmd_works "$(command -v java)"; then
    JAVA_BIN="$(command -v java)"
  elif [ -x "/opt/homebrew/opt/openjdk/bin/java" ]; then
    JAVA_BIN="/opt/homebrew/opt/openjdk/bin/java"
  else
    record_gap "java is missing"
  fi

  detect_java_home

  if have_cmd javac && javac_cmd_works "$(command -v javac)"; then
    JAVAC_BIN="$(command -v javac)"
  elif [ -n "${JAVA_HOME_FOUND}" ] && [ -x "${JAVA_HOME_FOUND}/bin/javac" ]; then
    JAVAC_BIN="${JAVA_HOME_FOUND}/bin/javac"
  else
    record_gap "javac is missing"
  fi

  if have_cmd gradle; then
    GRADLE_BIN="$(command -v gradle)"
  elif [ -n "${GRADLEW_PATH}" ]; then
    GRADLE_BIN="${GRADLEW_PATH}"
  fi

  if [ -n "${ANDROID_SDK_ROOT_CANDIDATE}" ] && [ -d "${ANDROID_SDK_ROOT_CANDIDATE}" ]; then
    ANDROID_SDK_ROOT_FOUND="${ANDROID_SDK_ROOT_CANDIDATE}"
  elif [ -d "${HOME}/Library/Android/sdk" ]; then
    ANDROID_SDK_ROOT_FOUND="${HOME}/Library/Android/sdk"
  fi

  if [ -n "${ANDROID_SDK_ROOT_FOUND}" ]; then
    if [ -x "${ANDROID_SDK_ROOT_FOUND}/cmdline-tools/latest/bin/sdkmanager" ]; then
      SDKMANAGER_BIN="${ANDROID_SDK_ROOT_FOUND}/cmdline-tools/latest/bin/sdkmanager"
    elif [ -x "${ANDROID_SDK_ROOT_FOUND}/cmdline-tools/bin/sdkmanager" ]; then
      SDKMANAGER_BIN="${ANDROID_SDK_ROOT_FOUND}/cmdline-tools/bin/sdkmanager"
    elif have_cmd sdkmanager; then
      SDKMANAGER_BIN="$(command -v sdkmanager)"
    fi

    if [ -x "${ANDROID_SDK_ROOT_FOUND}/platform-tools/adb" ]; then
      ADB_BIN="${ANDROID_SDK_ROOT_FOUND}/platform-tools/adb"
    elif have_cmd adb; then
      ADB_BIN="$(command -v adb)"
    fi

    if [ -x "${ANDROID_SDK_ROOT_FOUND}/emulator/emulator" ]; then
      EMULATOR_BIN="${ANDROID_SDK_ROOT_FOUND}/emulator/emulator"
    elif have_cmd emulator; then
      EMULATOR_BIN="$(command -v emulator)"
    fi

    ZIPALIGN_BIN="$(find "${ANDROID_SDK_ROOT_FOUND}/build-tools" -type f -name zipalign 2>/dev/null | sort -V | tail -n 1)"
    APKSIGNER_BIN="$(find "${ANDROID_SDK_ROOT_FOUND}/build-tools" -type f -name apksigner 2>/dev/null | sort -V | tail -n 1)"
  fi

  if [ -z "${ANDROID_SDK_ROOT_FOUND}" ]; then
    record_gap "Android SDK root is missing"
  fi
  if [ -z "${SDKMANAGER_BIN}" ]; then
    record_gap "sdkmanager is missing"
  fi
  if [ -z "${ADB_BIN}" ]; then
    record_gap "adb is missing"
  fi
  if [ -z "${ZIPALIGN_BIN}" ]; then
    record_gap "zipalign is missing"
  fi
  if [ -z "${APKSIGNER_BIN}" ]; then
    record_gap "apksigner is missing"
  fi

  if have_cmd bundletool; then
    BUNDLETOOL_BIN="$(command -v bundletool)"
  fi

  if have_cmd node; then
    NODE_BIN="$(command -v node)"
  fi
  if have_cmd npm; then
    NPM_BIN="$(command -v npm)"
  fi
  if have_cmd yarn; then
    YARN_BIN="$(command -v yarn)"
  fi
  if have_cmd pnpm; then
    PNPM_BIN="$(command -v pnpm)"
  fi

  if [ -n "${XCODEBUILD_BIN}" ] && [ -n "${XCRUN_BIN}" ]; then
    XCODE_PATH="$(${XCRUN_BIN} --find xcodebuild 2>/dev/null || true)"
  fi
}

install_lightweight_tools() {
  if [ "${NO_INSTALL}" = "1" ]; then
    return 0
  fi

  if [ "${ALLOW_BREW_INSTALL}" != "1" ]; then
    return 0
  fi

  if ! have_cmd brew; then
    record_gap "Homebrew is unavailable for optional tool installation"
    return 0
  fi

  if [ -z "${POD_BIN}" ]; then
    brew install cocoapods >/dev/null || record_gap "Failed to install CocoaPods with Homebrew"
    if have_cmd pod; then
      POD_BIN="$(command -v pod)"
      record_action "Installed CocoaPods with Homebrew"
    fi
  fi

  if [ -z "${BUNDLETOOL_BIN}" ]; then
    brew install bundletool >/dev/null || record_gap "Failed to install bundletool with Homebrew"
    if have_cmd bundletool; then
      BUNDLETOOL_BIN="$(command -v bundletool)"
      record_action "Installed bundletool with Homebrew"
    fi
  fi

  if [ -z "${GRADLE_BIN}" ] && [ -z "${GRADLEW_PATH}" ]; then
    brew install gradle >/dev/null || record_gap "Failed to install Gradle with Homebrew"
    if have_cmd gradle; then
      GRADLE_BIN="$(command -v gradle)"
      record_action "Installed Gradle with Homebrew"
    fi
  fi
}

ensure_tooling_venv() {
  if [ -z "${PYTHON3_BIN}" ]; then
    record_gap "repo-local app-init virtualenv could not be created because python3 is missing"
    return 1
  fi

  if [ -d "${TOOLING_VENV}" ]; then
    TOOLING_VENV_STATUS="reused"
    return 0
  fi

  mkdir -p "$(dirname "${TOOLING_VENV}")"
  if "${PYTHON3_BIN}" -m venv "${TOOLING_VENV}"; then
    TOOLING_VENV_STATUS="created"
    record_action "Created app-init virtualenv at ${TOOLING_VENV}"
    return 0
  fi

  TOOLING_VENV_STATUS="failed"
  record_gap "failed to create repo-local app-init virtualenv at ${TOOLING_VENV}"
  return 1
}

ensure_artifact_dirs() {
  if [ "${CREATE_ARTIFACT_DIRS}" != "1" ]; then
    return 0
  fi

  mkdir -p "${IOS_ARTIFACT_DIR}" "${ANDROID_ARTIFACT_DIR}"
  record_action "Ensured app artifact directories at ${IOS_ARTIFACT_DIR} and ${ANDROID_ARTIFACT_DIR}"
}

create_initial_scaffold() {
  if [ "${NO_INSTALL}" = "1" ]; then
    record_gap "initial app scaffold is missing and APP_INIT_NO_INSTALL=1 prevented scaffold creation"
    return 0
  fi

  if [ "${CREATE_SCAFFOLD}" != "1" ]; then
    record_gap "initial app scaffold is missing and AIRHEALTH_APP_CREATE_SCAFFOLD=0 disabled scaffold creation"
    return 0
  fi

  if [ "${SCAFFOLD_STATUS}" != "missing" ]; then
    return 0
  fi

  mkdir -p \
    "${APP_ROOT}/ios/AirHealthApp/App" \
    "${APP_ROOT}/ios/AirHealthApp/Features" \
    "${APP_ROOT}/android/app/src/main/java/com/airhealth/app" \
    "${APP_ROOT}/android/app/src/main/res/values" \
    "${APP_ROOT}/shared"

  if [ ! -f "${APP_ROOT}/README.md" ]; then
    cat > "${APP_ROOT}/README.md" <<'EOF'
# AirHealth Mobile App

This folder contains the AirHealth consumer mobile app workspace.

The current baseline scaffold is native-first because the product and architecture documents require iOS and Android support but do not yet pin a cross-platform runtime.

Layout:
- `ios/`: iOS app baseline sources and host-specific setup files
- `android/`: Android app baseline sources and Gradle build files
- `shared/`: cross-platform product contracts, view-model notes, and future shared code only when the repo later establishes a concrete sharing strategy
- `SKILLS/`: Codex mobile workflow skills
- `artifacts/`: local build outputs and packaging artifacts
EOF
  fi

  if [ ! -f "${APP_ROOT}/.gitignore" ]; then
    cat > "${APP_ROOT}/.gitignore" <<'EOF'
.venv-app-init/
.app-init.env
initialize.rpt
manager.rpt
artifacts/
ios/Pods/
android/.gradle/
android/local.properties
android/app/build/
EOF
  fi

  if [ ! -f "${APP_ROOT}/shared/README.md" ]; then
    cat > "${APP_ROOT}/shared/README.md" <<'EOF'
# Shared Mobile Notes

Use this folder for contracts, view-model definitions, fixtures, or portable logic only after the concrete app stack is established.

Do not treat this baseline scaffold as proof of a specific cross-platform framework.
EOF
  fi

  if [ ! -f "${APP_ROOT}/ios/Gemfile" ]; then
    cat > "${APP_ROOT}/ios/Gemfile" <<'EOF'
source "https://rubygems.org"

gem "cocoapods"
EOF
  fi

  if [ ! -f "${APP_ROOT}/ios/Podfile" ]; then
    cat > "${APP_ROOT}/ios/Podfile" <<'EOF'
platform :ios, '18.0'

target 'AirHealthApp' do
  use_frameworks!
end
EOF
  fi

  if [ ! -f "${APP_ROOT}/ios/AirHealthApp/App/AirHealthApp.swift" ]; then
    cat > "${APP_ROOT}/ios/AirHealthApp/App/AirHealthApp.swift" <<'EOF'
import SwiftUI

@main
struct AirHealthApp: App {
    var body: some Scene {
        WindowGroup {
            Text("AirHealth")
                .padding()
        }
    }
}
EOF
  fi

  if [ ! -f "${APP_ROOT}/ios/AirHealthApp/Features/README.md" ]; then
    cat > "${APP_ROOT}/ios/AirHealthApp/Features/README.md" <<'EOF'
# iOS Features

Add feature modules here as the app implementation takes shape.
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/settings.gradle.kts" ]; then
    cat > "${APP_ROOT}/android/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AirHealth"
include(":app")
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/build.gradle.kts" ]; then
    cat > "${APP_ROOT}/android/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/gradle.properties" ]; then
    cat > "${APP_ROOT}/android/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/app/build.gradle.kts" ]; then
    cat > "${APP_ROOT}/android/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.airhealth.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.airhealth.app"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
}
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/app/proguard-rules.pro" ]; then
    cat > "${APP_ROOT}/android/app/proguard-rules.pro" <<'EOF'
# AirHealth app-specific ProGuard rules.
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/app/src/main/AndroidManifest.xml" ]; then
    cat > "${APP_ROOT}/android/app/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/app/src/main/java/com/airhealth/app/MainActivity.kt" ]; then
    cat > "${APP_ROOT}/android/app/src/main/java/com/airhealth/app/MainActivity.kt" <<'EOF'
package com.airhealth.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "AirHealth"
            textSize = 24f
            setPadding(32, 32, 32, 32)
        }

        setContentView(textView)
    }
}
EOF
  fi

  if [ ! -f "${APP_ROOT}/android/app/src/main/res/values/strings.xml" ]; then
    cat > "${APP_ROOT}/android/app/src/main/res/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name">AirHealth</string>
</resources>
EOF
  fi

  SCAFFOLD_STATUS="created"
  SCAFFOLD_KIND="native-first baseline scaffold"
  record_action "Created initial native-first app scaffold under ${APP_ROOT}/ios and ${APP_ROOT}/android"
}

write_env_bootstrap() {
  mkdir -p "$(dirname "${BOOTSTRAP_ENV_PATH}")"

  {
    printf '# Generated by APP-init on %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf '# Source this file to reuse the AirHealth mobile helper environment.\n'
    printf '# Python helper tooling lives in the repo-local virtualenv.\n'
    printf '# Xcode, CocoaPods, Android SDK, JDK, and Gradle stay in the host toolchain.\n\n'
    printf 'export AIRHEALTH_APP_TOOLING_VENV="%s"\n' "${TOOLING_VENV}"
    printf 'if [ -d "%s/bin" ]; then\n' "${TOOLING_VENV}"
    printf '  export PATH="%s/bin:$PATH"\n' "${TOOLING_VENV}"
    printf 'fi\n\n'

    if [ -n "${JAVA_HOME_FOUND}" ]; then
      printf 'export JAVA_HOME="%s"\n' "${JAVA_HOME_FOUND}"
      printf 'if [ -d "%s/bin" ]; then\n' "${JAVA_HOME_FOUND}"
      printf '  export PATH="%s/bin:$PATH"\n' "${JAVA_HOME_FOUND}"
      printf 'fi\n\n'
    else
      printf '# JAVA_HOME is not configured yet.\n'
      printf '# If you install a host JDK outside the system stub, export JAVA_HOME here.\n\n'
    fi

    if [ -n "${ANDROID_SDK_ROOT_FOUND}" ]; then
      printf 'export ANDROID_SDK_ROOT="%s"\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf 'export ANDROID_HOME="%s"\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf 'if [ -d "%s/platform-tools" ]; then\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf '  export PATH="%s/platform-tools:$PATH"\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf 'fi\n'
      printf 'if [ -d "%s/emulator" ]; then\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf '  export PATH="%s/emulator:$PATH"\n' "${ANDROID_SDK_ROOT_FOUND}"
      printf 'fi\n'
      if [ -n "${SDKMANAGER_BIN}" ]; then
        printf '# sdkmanager detected at %s\n' "${SDKMANAGER_BIN}"
      else
        printf '# Install Android command-line tools with Android Studio SDK Manager.\n'
      fi
    else
      printf '# ANDROID_SDK_ROOT is not configured yet.\n'
      printf '# Install Android Studio and SDK command-line tools, then set ANDROID_SDK_ROOT here.\n'
      printf '# export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"\n'
      printf '# export ANDROID_HOME="$ANDROID_SDK_ROOT"\n'
    fi
  } > "${BOOTSTRAP_ENV_PATH}"

  record_action "Wrote app environment bootstrap at ${BOOTSTRAP_ENV_PATH}"
}

compute_package_readiness() {
  if [ -n "${XCODEBUILD_BIN}" ] && [ -n "${XCRUN_BIN}" ]; then
    if [ "${SCAFFOLD_STATUS}" = "missing" ]; then
      IOS_PACKAGE_READINESS="partial: no iOS app scaffold exists yet"
    elif [ -n "${PODFILE_PATH}" ] && [ -z "${POD_BIN}" ] && [ -z "${BUNDLE_BIN}" ]; then
      IOS_PACKAGE_READINESS="partial: Podfile present but CocoaPods or Bundler is missing from the host toolchain"
    else
      IOS_PACKAGE_READINESS="ready: Xcode command-line tooling is available"
    fi
  else
    IOS_PACKAGE_READINESS="partial: Xcode command-line tooling is incomplete"
  fi

  if [ "${SCAFFOLD_STATUS}" = "missing" ]; then
    ANDROID_PACKAGE_READINESS="partial: no Android app scaffold exists yet"
  elif [ -n "${JAVA_BIN}" ] && [ -n "${JAVAC_BIN}" ] && [ -n "${ANDROID_SDK_ROOT_FOUND}" ] && [ -n "${SDKMANAGER_BIN}" ] && [ -n "${ADB_BIN}" ] && [ -n "${ZIPALIGN_BIN}" ] && [ -n "${APKSIGNER_BIN}" ]; then
    if [ -n "${GRADLE_BIN}" ] || [ -n "${GRADLEW_PATH}" ]; then
      ANDROID_PACKAGE_READINESS="ready: Android SDK and build tooling are available"
    else
      ANDROID_PACKAGE_READINESS="partial: Android SDK is present but Gradle or gradlew is still missing"
    fi
  else
    ANDROID_PACKAGE_READINESS="partial: Android SDK and packaging tools are incomplete"
  fi
}

compute_status() {
  if [ -n "${ERROR_MESSAGE}" ] && [ "${STATUS}" = "BLOCKED" ]; then
    return 0
  fi

  if [ "${SCAFFOLD_STATUS}" = "missing" ] || [ -z "${XCODEBUILD_BIN}" ] || [ -z "${XCRUN_BIN}" ] || [ -z "${JAVA_BIN}" ] || [ -z "${JAVAC_BIN}" ] || [ -z "${ANDROID_SDK_ROOT_FOUND}" ] || [ -z "${SDKMANAGER_BIN}" ] || [ -z "${ADB_BIN}" ] || [ -z "${ZIPALIGN_BIN}" ] || [ -z "${APKSIGNER_BIN}" ]; then
    STATUS="PARTIAL"
  else
    STATUS="READY"
  fi
}

write_report() {
  mkdir -p "$(dirname "${REPORT_PATH}")"

  {
    printf 'App initialization report\n'
    printf 'timestamp: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'prd: %s\n' "${PRD_USED}"
    printf 'mobile_design: %s\n' "${LATEST_MOBILE_DESIGN}"
    printf 'architecture_spec: %s\n' "${LATEST_ARCH_SPEC}"
    printf 'mobile_baseline: %s\n' "${MOBILE_BASELINE}"
    printf 'framework_hint: %s\n' "${FRAMEWORK_HINT}"
    printf 'scaffold_status: %s\n' "${SCAFFOLD_STATUS}"
    printf 'scaffold_kind: %s\n' "${SCAFFOLD_KIND}"
    printf 'tooling_venv: %s\n' "${TOOLING_VENV}"
    printf 'tooling_venv_status: %s\n' "${TOOLING_VENV_STATUS}"
    printf 'env_bootstrap: %s\n' "${BOOTSTRAP_ENV_PATH}"
    printf 'tooling_layer_python: repo-local virtualenv for helper tooling only\n'
    printf 'tooling_layer_ios: host Xcode plus Bundler or CocoaPods\n'
    printf 'tooling_layer_android: host Android SDK plus JDK plus Gradle or gradlew\n'
    printf 'xcodebuild: %s\n' "${XCODEBUILD_BIN:-missing}"
    printf 'xcrun: %s\n' "${XCRUN_BIN:-missing}"
    printf 'swift: %s\n' "${SWIFT_BIN:-missing}"
    printf 'cocoapods: %s\n' "${POD_BIN:-missing}"
    printf 'bundler: %s\n' "${BUNDLE_BIN:-missing}"
    printf 'java: %s\n' "${JAVA_BIN:-missing}"
    printf 'javac: %s\n' "${JAVAC_BIN:-missing}"
    printf 'java_home: %s\n' "${JAVA_HOME_FOUND:-missing}"
    printf 'gradle: %s\n' "${GRADLE_BIN:-missing}"
    printf 'android_sdk_root: %s\n' "${ANDROID_SDK_ROOT_FOUND:-missing}"
    printf 'sdkmanager: %s\n' "${SDKMANAGER_BIN:-missing}"
    printf 'adb: %s\n' "${ADB_BIN:-missing}"
    printf 'emulator: %s\n' "${EMULATOR_BIN:-missing}"
    printf 'zipalign: %s\n' "${ZIPALIGN_BIN:-missing}"
    printf 'apksigner: %s\n' "${APKSIGNER_BIN:-missing}"
    printf 'bundletool: %s\n' "${BUNDLETOOL_BIN:-missing}"
    printf 'node: %s\n' "${NODE_BIN:-missing}"
    printf 'npm: %s\n' "${NPM_BIN:-missing}"
    printf 'yarn: %s\n' "${YARN_BIN:-missing}"
    printf 'pnpm: %s\n' "${PNPM_BIN:-missing}"
    printf 'package_json: %s\n' "${PACKAGE_JSON_PATH:-missing}"
    printf 'app_json: %s\n' "${APP_JSON_PATH:-missing}"
    printf 'expo_json: %s\n' "${EXPO_JSON_PATH:-missing}"
    printf 'xcodeproj: %s\n' "${XCODEPROJ_PATH:-missing}"
    printf 'podfile: %s\n' "${PODFILE_PATH:-missing}"
    printf 'gemfile: %s\n' "${GEMFILE_PATH:-missing}"
    printf 'android_settings: %s\n' "${ANDROID_SETTINGS_PATH:-missing}"
    printf 'android_app_build: %s\n' "${ANDROID_APP_BUILD_PATH:-missing}"
    printf 'gradlew: %s\n' "${GRADLEW_PATH:-missing}"
    printf 'ios_artifact_dir: %s\n' "${IOS_ARTIFACT_DIR}"
    printf 'android_artifact_dir: %s\n' "${ANDROID_ARTIFACT_DIR}"
    printf 'ios_package_readiness: %s\n' "${IOS_PACKAGE_READINESS}"
    printf 'android_package_readiness: %s\n' "${ANDROID_PACKAGE_READINESS}"
    if [ -n "${ERROR_MESSAGE}" ]; then
      printf 'error: %s\n' "${ERROR_MESSAGE}"
    fi
    if [ "${#ACTIONS_TAKEN[@]}" -gt 0 ]; then
      join_lines 'action:' "${ACTIONS_TAKEN[@]}"
    else
      printf 'action: none\n'
    fi
    if [ "${#REMAINING_GAPS[@]}" -gt 0 ]; then
      join_lines 'gap:' "${REMAINING_GAPS[@]}"
    else
      printf 'gap: none\n'
    fi
    printf 'status: %s\n' "${STATUS}"
  } > "${REPORT_PATH}"
}

main() {
  if ! detect_latest_docs; then
    write_report
    exit 1
  fi

  detect_project_files
  infer_mobile_baseline
  check_tooling
  ensure_tooling_venv || true
  install_lightweight_tools
  ensure_artifact_dirs
  create_initial_scaffold
  detect_project_files
  infer_mobile_baseline
  write_env_bootstrap
  compute_package_readiness
  compute_status
  write_report

  if [ "${STATUS}" = "BLOCKED" ]; then
    exit 1
  fi

  exit 0
}

main "$@"
