#!/usr/bin/env bash

set -u -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

find_airhealth_root() {
  local start_dir="$1"
  local current="${start_dir}"

  while [ "${current}" != "/" ]; do
    if [ -d "${current}/HW/EE" ] && [ -d "${current}/SW/FW" ]; then
      printf '%s\n' "${current}"
      return 0
    fi
    current="$(dirname "${current}")"
  done

  return 1
}

REPO_ROOT=""
if [ -n "${AIRHEALTH_REPO_ROOT:-}" ] && [ -d "${AIRHEALTH_REPO_ROOT}/HW/EE" ] && [ -d "${AIRHEALTH_REPO_ROOT}/SW/FW" ]; then
  REPO_ROOT="${AIRHEALTH_REPO_ROOT}"
elif REPO_ROOT="$(find_airhealth_root "$(pwd)")"; then
  :
elif REPO_ROOT="$(find_airhealth_root "${SKILL_DIR}")"; then
  :
else
  REPO_ROOT="$(cd "${SKILL_DIR}/../../../.." && pwd)"
fi

REPORT_PATH="${REPO_ROOT}/SW/FW/initialize.rpt"
EE_SPEC_DIR="${REPO_ROOT}/HW/EE"
DEFAULT_SDK_ROOT="${REPO_ROOT}/SW/FW/vendor-sdk/nordic/ncs"
DEFAULT_TOOLING_VENV="${REPO_ROOT}/SW/FW/vendor-sdk/.venv-fw-init"
ALLOW_GLOBAL_SDK="${AIRHEALTH_FW_ALLOW_GLOBAL_SDK:-0}"

STATUS="PARTIAL"
ERROR_MESSAGE=""
ACTIONS_TAKEN=()
REMAINING_GAPS=()

LATEST_EE_SPEC=""
EE_SPEC_VERSION="unknown"
MCU_TARGET="unknown"
MCU_VENDOR="unknown"
SDK_NAME="unknown"
SDK_REPO=""
SDK_REF="${AIRHEALTH_FW_SDK_REF:-}"
SDK_SELECTED_REF=""
SDK_ROOT="${AIRHEALTH_FW_SDK_DIR:-${DEFAULT_SDK_ROOT}}"
TOOLING_VENV="${AIRHEALTH_FW_TOOLING_VENV:-${DEFAULT_TOOLING_VENV}}"
NO_INSTALL="${FW_INIT_NO_INSTALL:-0}"
WEST_BIN=""
SDK_PATH_FOUND=""
SDK_ACTION="none"

PYTHON3_BIN=""
CMAKE_BIN=""
NINJA_BIN=""
NRFUTIL_BIN=""
ARM_ZEPHYR_EABI_BIN=""
ARM_NONE_EABI_BIN=""

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

detect_latest_spec() {
  local latest
  latest="$(find "${EE_SPEC_DIR}" -maxdepth 1 -type f -name 'EE_Design_Spec_v*.md' | sort -V | tail -n 1)"
  if [ -z "${latest}" ]; then
    ERROR_MESSAGE="No EE design spec was found under ${EE_SPEC_DIR}."
    STATUS="BLOCKED"
    return 1
  fi

  LATEST_EE_SPEC="${latest}"
  EE_SPEC_VERSION="$(basename "${LATEST_EE_SPEC}")"
  return 0
}

detect_target() {
  if grep -Eqi 'nRF5340|Nordic' "${LATEST_EE_SPEC}"; then
    MCU_TARGET="nRF5340-class BLE SoC / MCU"
    MCU_VENDOR="Nordic"
    SDK_NAME="nRF Connect SDK"
    SDK_REPO="https://github.com/nrfconnect/sdk-nrf.git"
    return 0
  fi

  if grep -Eqi 'STM32' "${LATEST_EE_SPEC}"; then
    MCU_TARGET="STM32-family MCU"
    MCU_VENDOR="STMicroelectronics"
    SDK_NAME="STM32Cube"
    return 0
  fi

  if grep -Eqi 'ESP32|Espressif' "${LATEST_EE_SPEC}"; then
    MCU_TARGET="ESP32-family MCU"
    MCU_VENDOR="Espressif"
    SDK_NAME="ESP-IDF"
    return 0
  fi

  STATUS="BLOCKED"
  ERROR_MESSAGE="Could not infer a supported MCU or vendor SDK from ${EE_SPEC_VERSION}."
  return 1
}

find_existing_sdk() {
  local candidates=()

  if [ -n "${SDK_ROOT}" ]; then
    candidates+=("${SDK_ROOT}")
  fi

  if [ "${MCU_VENDOR}" = "Nordic" ] && [ "${ALLOW_GLOBAL_SDK}" = "1" ]; then
    candidates+=(
      "${HOME}/.cache/airhealth/fw-sdk/nordic/ncs"
      "${HOME}/ncs"
      "${HOME}/.ncs"
    )
  fi

  local candidate
  for candidate in "${candidates[@]}"; do
    if [ -d "${candidate}/.west" ] || [ -d "${candidate}/nrf" ] || [ -f "${candidate}/west.yml" ]; then
      SDK_PATH_FOUND="${candidate}"
      SDK_ROOT="${candidate}"
      SDK_ACTION="reused existing SDK"
      return 0
    fi
  done

  return 1
}

check_tooling() {
  if have_cmd python3; then
    PYTHON3_BIN="$(command -v python3)"
  else
    record_gap "python3 is missing"
  fi

  if have_cmd west; then
    WEST_BIN="$(command -v west)"
  elif [ -x "${TOOLING_VENV}/bin/west" ]; then
    WEST_BIN="${TOOLING_VENV}/bin/west"
  fi

  if have_cmd cmake; then
    CMAKE_BIN="$(command -v cmake)"
  else
    record_gap "cmake is missing"
  fi

  if have_cmd ninja; then
    NINJA_BIN="$(command -v ninja)"
  else
    record_gap "ninja is missing"
  fi

  if have_cmd nrfutil; then
    NRFUTIL_BIN="$(command -v nrfutil)"
  fi

  if have_cmd arm-zephyr-eabi-gcc; then
    ARM_ZEPHYR_EABI_BIN="$(command -v arm-zephyr-eabi-gcc)"
  fi

  if have_cmd arm-none-eabi-gcc; then
    ARM_NONE_EABI_BIN="$(command -v arm-none-eabi-gcc)"
  fi

  if [ -z "${ARM_ZEPHYR_EABI_BIN}" ] && [ -z "${ARM_NONE_EABI_BIN}" ]; then
    record_gap "ARM cross-compiler is missing"
  fi
}

ensure_west() {
  if [ -n "${WEST_BIN}" ]; then
    return 0
  fi

  if [ -z "${PYTHON3_BIN}" ]; then
    STATUS="BLOCKED"
    ERROR_MESSAGE="python3 is required to bootstrap west for ${SDK_NAME}."
    return 1
  fi

  mkdir -p "$(dirname "${TOOLING_VENV}")"

  if [ ! -d "${TOOLING_VENV}" ]; then
    "${PYTHON3_BIN}" -m venv "${TOOLING_VENV}"
    if [ "$?" -ne 0 ]; then
      STATUS="BLOCKED"
      ERROR_MESSAGE="Failed to create tooling virtualenv at ${TOOLING_VENV}."
      return 1
    fi
    record_action "Created tooling virtualenv at ${TOOLING_VENV}"
  fi

  "${TOOLING_VENV}/bin/pip" install --upgrade pip west >/dev/null
  if [ "$?" -ne 0 ]; then
    STATUS="BLOCKED"
    ERROR_MESSAGE="Failed to install west into ${TOOLING_VENV}."
    return 1
  fi

  WEST_BIN="${TOOLING_VENV}/bin/west"
  record_action "Installed west into ${TOOLING_VENV}"
  return 0
}

discover_nordic_ref() {
  if [ -n "${SDK_REF}" ]; then
    return 0
  fi

  if have_cmd git; then
    SDK_REF="$(
      git ls-remote --tags "${SDK_REPO}" 'refs/tags/v*' 2>/dev/null \
        | awk -F/ '{print $3}' \
        | sed 's/\^{}//' \
        | grep -Evi 'rc|alpha|beta|preview|snapshot|dev' \
        | sort -Vu \
        | tail -n 1
    )"
  fi

  if [ -z "${SDK_REF}" ]; then
    SDK_REF="main"
    record_action "Fell back to ${SDK_REF} because no stable SDK tag could be discovered automatically"
  fi

  return 0
}

install_nordic_sdk_if_needed() {
  if [ -n "${SDK_PATH_FOUND}" ]; then
    SDK_ACTION="reused existing SDK"
    return 0
  fi

  if ! ensure_west; then
    return 1
  fi

  discover_nordic_ref

  mkdir -p "$(dirname "${SDK_ROOT}")"

  if [ ! -d "${SDK_ROOT}/.west" ]; then
    "${WEST_BIN}" init -m "${SDK_REPO}" --mr "${SDK_REF}" "${SDK_ROOT}"
    if [ "$?" -ne 0 ]; then
      STATUS="BLOCKED"
      ERROR_MESSAGE="Failed to initialize ${SDK_NAME} at ${SDK_ROOT}."
      return 1
    fi
    record_action "Initialized ${SDK_NAME} workspace at ${SDK_ROOT}"
  fi

  (
    cd "${SDK_ROOT}" && "${WEST_BIN}" update
  )
  if [ "$?" -ne 0 ]; then
    STATUS="BLOCKED"
    ERROR_MESSAGE="Failed to update ${SDK_NAME} workspace at ${SDK_ROOT}."
    return 1
  fi

  SDK_PATH_FOUND="${SDK_ROOT}"
  SDK_ACTION="installed vendor SDK"
  record_action "Updated ${SDK_NAME} modules in ${SDK_ROOT}"
  return 0
}

write_report() {
  mkdir -p "$(dirname "${REPORT_PATH}")"

  if [ -z "${SDK_PATH_FOUND}" ] && [ -n "${SDK_ROOT}" ] && [ -d "${SDK_ROOT}" ]; then
    SDK_PATH_FOUND="${SDK_ROOT}"
  fi

  if [ -z "${SDK_SELECTED_REF}" ] && [ -n "${SDK_PATH_FOUND}" ] && [ -d "${SDK_PATH_FOUND}/nrf/.git" ]; then
    SDK_SELECTED_REF="$(git -C "${SDK_PATH_FOUND}/nrf" describe --tags --always 2>/dev/null || true)"
  fi

  if [ -n "${SDK_SELECTED_REF}" ] && printf '%s' "${SDK_SELECTED_REF}" | grep -Eqi 'preview|rc|snapshot|dev'; then
    record_gap "Installed SDK revision appears to be pre-release: ${SDK_SELECTED_REF}"
  fi

  if [ "${STATUS}" != "BLOCKED" ]; then
  if [ -n "${SDK_PATH_FOUND}" ] && [ -n "${WEST_BIN}" ] && [ -n "${CMAKE_BIN}" ] && [ -n "${NINJA_BIN}" ] && { [ -n "${ARM_ZEPHYR_EABI_BIN}" ] || [ -n "${ARM_NONE_EABI_BIN}" ]; }; then
      STATUS="READY"
    else
      STATUS="PARTIAL"
    fi
  fi

  {
    printf -- "AirHealth Firmware Initialization Report\n"
    printf -- "Generated at: %s\n" "$(date '+%Y-%m-%d %H:%M:%S %Z')"
    printf -- "\n"
    printf -- "Status: %s\n" "${STATUS}"
    if [ -n "${ERROR_MESSAGE}" ]; then
      printf -- "Error: %s\n" "${ERROR_MESSAGE}"
    fi
    printf -- "\n"
    printf -- "Inputs\n"
    printf -- "- EE design spec: %s\n" "${LATEST_EE_SPEC:-not found}"
    printf -- "- MCU target: %s\n" "${MCU_TARGET}"
    printf -- "- Vendor: %s\n" "${MCU_VENDOR}"
    printf -- "- Expected SDK: %s\n" "${SDK_NAME}"
    printf -- "\n"
    printf -- "Environment Check\n"
    printf -- "- python3: %s\n" "${PYTHON3_BIN:-missing}"
    printf -- "- west: %s\n" "${WEST_BIN:-missing}"
    printf -- "- cmake: %s\n" "${CMAKE_BIN:-missing}"
    printf -- "- ninja: %s\n" "${NINJA_BIN:-missing}"
    printf -- "- arm-zephyr-eabi-gcc: %s\n" "${ARM_ZEPHYR_EABI_BIN:-missing}"
    printf -- "- arm-none-eabi-gcc: %s\n" "${ARM_NONE_EABI_BIN:-missing}"
    printf -- "- nrfutil: %s\n" "${NRFUTIL_BIN:-missing}"
    printf -- "- SDK path: %s\n" "${SDK_PATH_FOUND:-missing}"
    printf -- "- SDK revision request: %s\n" "${SDK_REF:-not set}"
    printf -- "- SDK revision resolved: %s\n" "${SDK_SELECTED_REF:-unknown}"
    printf -- "- SDK action: %s\n" "${SDK_ACTION}"
    printf -- "\n"
    printf -- "Actions Taken\n"
    if [ "${#ACTIONS_TAKEN[@]}" -gt 0 ]; then
      join_lines "- " "${ACTIONS_TAKEN[@]}"
    else
      join_lines "- "
    fi
    printf -- "\n"
    printf -- "Remaining Gaps\n"
    if [ "${#REMAINING_GAPS[@]}" -gt 0 ]; then
      join_lines "- " "${REMAINING_GAPS[@]}"
    else
      join_lines "- "
    fi
  } > "${REPORT_PATH}"
}

main() {
  detect_latest_spec || true

  if [ -z "${LATEST_EE_SPEC}" ]; then
    write_report
    return 1
  fi

  detect_target || true
  check_tooling
  find_existing_sdk || true

  if [ "${MCU_VENDOR}" = "Nordic" ] && [ -z "${SDK_PATH_FOUND}" ]; then
    if [ "${NO_INSTALL}" = "1" ]; then
      record_action "Skipped SDK installation because FW_INIT_NO_INSTALL=1"
    else
      install_nordic_sdk_if_needed || true
    fi
  elif [ "${MCU_VENDOR}" != "Nordic" ] && [ "${MCU_VENDOR}" != "unknown" ]; then
    record_gap "Automatic SDK installation is not yet implemented for ${MCU_VENDOR}"
  fi

  if [ -z "${SDK_PATH_FOUND}" ]; then
    record_gap "${SDK_NAME} checkout is missing"
  fi

  write_report

  if [ "${STATUS}" = "BLOCKED" ]; then
    return 1
  fi

  return 0
}

main "$@"
