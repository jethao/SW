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

NO_INSTALL="${APP_INIT_NO_INSTALL:-0}"
ALLOW_BREW_INSTALL="${AIRHEALTH_APP_ALLOW_BREW_INSTALL:-0}"
CREATE_ARTIFACT_DIRS="${AIRHEALTH_APP_CREATE_ARTIFACT_DIRS:-1}"
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

infer_mobile_baseline() {
  if grep -Eqi 'iOS 26\+|Android 16\+' "${LATEST_MOBILE_DESIGN}" "${LATEST_ARCH_SPEC}" 2>/dev/null; then
    MOBILE_BASELINE="iOS 26+ and Android 16+"
  else
    MOBILE_BASELINE="mobile baseline not explicitly versioned"
  fi

  if find "${APP_ROOT}" -maxdepth 3 \( -name package.json -o -name app.json -o -name expo.json \) | grep -q .; then
    FRAMEWORK_HINT="js-based mobile stack present"
  elif find "${APP_ROOT}" -maxdepth 3 \( -name Podfile -o -name '*.xcodeproj' -o -name '*.xcworkspace' \) | grep -q .; then
    FRAMEWORK_HINT="native iOS scaffolding present"
  elif find "${APP_ROOT}" -maxdepth 3 \( -name build.gradle -o -name build.gradle.kts -o -name settings.gradle -o -name settings.gradle.kts \) | grep -q .; then
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

  if have_cmd java; then
    JAVA_BIN="$(command -v java)"
  else
    record_gap "java is missing"
  fi

  if have_cmd javac; then
    JAVAC_BIN="$(command -v javac)"
  else
    record_gap "javac is missing"
  fi

  if have_cmd gradle; then
    GRADLE_BIN="$(command -v gradle)"
  elif [ -x "${APP_ROOT}/gradlew" ]; then
    GRADLE_BIN="${APP_ROOT}/gradlew"
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

compute_status() {
  if [ -n "${ERROR_MESSAGE}" ] && [ "${STATUS}" = "BLOCKED" ]; then
    return 0
  fi

  if [ -z "${XCODEBUILD_BIN}" ] || [ -z "${XCRUN_BIN}" ] || [ -z "${JAVA_BIN}" ] || [ -z "${JAVAC_BIN}" ] || [ -z "${ANDROID_SDK_ROOT_FOUND}" ] || [ -z "${SDKMANAGER_BIN}" ] || [ -z "${ADB_BIN}" ] || [ -z "${ZIPALIGN_BIN}" ] || [ -z "${APKSIGNER_BIN}" ]; then
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
    printf 'tooling_venv: %s\n' "${TOOLING_VENV}"
    printf 'tooling_venv_status: %s\n' "${TOOLING_VENV_STATUS}"
    printf 'xcodebuild: %s\n' "${XCODEBUILD_BIN:-missing}"
    printf 'xcrun: %s\n' "${XCRUN_BIN:-missing}"
    printf 'swift: %s\n' "${SWIFT_BIN:-missing}"
    printf 'cocoapods: %s\n' "${POD_BIN:-missing}"
    printf 'bundler: %s\n' "${BUNDLE_BIN:-missing}"
    printf 'java: %s\n' "${JAVA_BIN:-missing}"
    printf 'javac: %s\n' "${JAVAC_BIN:-missing}"
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
    printf 'ios_artifact_dir: %s\n' "${IOS_ARTIFACT_DIR}"
    printf 'android_artifact_dir: %s\n' "${ANDROID_ARTIFACT_DIR}"
    if [ -n "${ERROR_MESSAGE}" ]; then
      printf 'error: %s\n' "${ERROR_MESSAGE}"
    fi
    join_lines 'action:' "${ACTIONS_TAKEN[@]}"
    join_lines 'gap:' "${REMAINING_GAPS[@]}"
    printf 'status: %s\n' "${STATUS}"
  } > "${REPORT_PATH}"
}

main() {
  if ! detect_latest_docs; then
    write_report
    exit 1
  fi

  infer_mobile_baseline
  check_tooling
  ensure_tooling_venv || true
  install_lightweight_tools
  ensure_artifact_dirs
  compute_status
  write_report

  if [ "${STATUS}" = "BLOCKED" ]; then
    exit 1
  fi

  exit 0
}

main "$@"
