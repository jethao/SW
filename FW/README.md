# AirHealth Firmware

## Overview

This directory contains the AirHealth firmware codebase, firmware workflow skills, repo-local SDK bootstrap assets, and firmware environment reports.

Main locations:

- `SW/FW/Source`: firmware source, tests, and build files
- `SW/FW/Source/app`: Zephyr or NCS application entry point
- `SW/FW/SKILLS`: firmware workflow skills used by Codex
- `SW/FW/vendor-sdk`: repo-local Nordic SDK and toolchain bootstrap area
- `SW/FW/initialize.rpt`: firmware environment status report
- `SW/FW/fw-mgr.rpt`: firmware manager workflow report

## Current Status

The checked-in firmware tree supports:

- host-native CMake builds for shared firmware modules under `SW/FW/Source`
- host-native unit tests for the current firmware modules under `SW/FW/Source/tests`
- a real Zephyr or NCS application entry point under `SW/FW/Source/app`
- an initial Nordic DK target for `nrf5340dk/nrf5340/cpuapp`
- repo-local Nordic SDK bootstrap under `SW/FW/vendor-sdk`

The checked-in board app is an initial hardware entry point, not a full production board package yet. It currently wires in the Zephyr app shell, BLE device-info service, and selected shared firmware modules while the broader firmware feature set continues to land in the shared source tree.

Not yet included:

- a custom AirHealth production board definition
- a repo-local `west.yml` for a standalone app manifest
- a fully productized device image flow beyond the Nordic DK baseline

## Environment Setup

### 1. Prerequisites

Expected local tools:

- `python3`
- `cmake`
- `ninja`
- `nrfutil`
- a Zephyr SDK installation, currently expected at `SW/FW/vendor-sdk/zephyr-sdk-0.17.4`

### 2. Bootstrap the repo-local Nordic SDK

From the AirHealth repo root:

```bash
bash SW/FW/SKILLS/fw-init/scripts/run_fw_init.sh
```

This script:

- reads the latest EE design spec under `HW/EE`
- infers the target MCU family
- checks the local firmware toolchain
- installs or reuses the repo-local Nordic SDK
- refreshes `SW/FW/initialize.rpt`

Default repo-local paths:

- NCS checkout: `SW/FW/vendor-sdk/nordic/ncs`
- helper venv: `SW/FW/vendor-sdk/.venv-fw-init`
- Zephyr SDK: `SW/FW/vendor-sdk/zephyr-sdk-0.17.4`

### 3. Load the board-build environment

From the AirHealth repo root:

```bash
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
```

Important:

- run these commands from the AirHealth repo root so `$PWD/SW/FW/...` resolves correctly
- keep `SW/FW/vendor-sdk/.venv-fw-init/bin` at the front of `PATH` so `west`, `cmake`, and Zephyr board-discovery scripts use the repo-local Python environment
- this avoids configure failures such as `ModuleNotFoundError: No module named 'pykwalify'`

### 4. Verify environment health

Open:

- `SW/FW/initialize.rpt`

Expected healthy state:

- `Status: READY`

## Build

Two build paths are supported today:

- host-native build for shared firmware modules and tests
- Zephyr or NCS build for the Nordic DK application entry point

### Host-native firmware build

From the AirHealth repo root:

```bash
cmake -S SW/FW/Source -B SW/FW/Source/build
cmake --build SW/FW/Source/build
```

This produces:

- the shared firmware library build products
- host-native unit-test binaries for the modules under `SW/FW/Source/tests`

### Zephyr or NCS board build

Current checked-in board target:

- `nrf5340dk/nrf5340/cpuapp`

From the AirHealth repo root:

```bash
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cd SW/FW/vendor-sdk/nordic/ncs
west build -p always -b nrf5340dk/nrf5340/cpuapp ../../../Source/app -d ../../../Source/app/build-nrf5340dk
```

Equivalent absolute-path form:

```bash
cd /Users/haohua/coding/AirHealth/SW/FW/vendor-sdk/nordic/ncs
PATH=/Users/haohua/coding/AirHealth/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH \
ZEPHYR_TOOLCHAIN_VARIANT=zephyr \
ZEPHYR_SDK_INSTALL_DIR=/Users/haohua/coding/AirHealth/SW/FW/vendor-sdk/zephyr-sdk-0.17.4 \
/Users/haohua/coding/AirHealth/SW/FW/vendor-sdk/.venv-fw-init/bin/west build -p always \
  -b nrf5340dk/nrf5340/cpuapp \
  /Users/haohua/coding/AirHealth/SW/FW/Source/app \
  -d /Users/haohua/coding/AirHealth/SW/FW/Source/app/build-nrf5340dk
```

Expected build outputs:

- `SW/FW/Source/app/build-nrf5340dk/app/zephyr/zephyr.elf`
- `SW/FW/Source/app/build-nrf5340dk/app/zephyr/zephyr.hex`
- `SW/FW/Source/app/build-nrf5340dk/merged.hex`

## Run Tests

From the AirHealth repo root:

```bash
ctest --test-dir SW/FW/Source/build --output-on-failure
```

This runs the current host-native firmware test suite, including:

- pairing
- session orchestration
- oral flow
- fat flow
- low power
- LED and button handling
- journaling
- routing
- OTA
- factory authorization
- factory diagnostics bundle handling

## Flash to Device

The current checked-in flash flow targets the Nordic DK app build.

### Preferred flash path

From the AirHealth repo root:

```bash
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cd SW/FW/vendor-sdk/nordic/ncs
west flash -d ../../../Source/app/build-nrf5340dk
```

### Alternate explicit-image flash path

From the AirHealth repo root:

```bash
nrfutil device program --firmware SW/FW/Source/app/build-nrf5340dk/merged.hex
```

Expected prerequisites:

- the board is connected over USB
- Nordic debug access is available on the host
- the `fw-init` flow has already populated `SW/FW/vendor-sdk`
- the Zephyr SDK is available at `SW/FW/vendor-sdk/zephyr-sdk-0.17.4`

## Repo Layout

- `SW/FW/Source/CMakeLists.txt`: host-native firmware build
- `SW/FW/Source/src`: shared firmware module implementations
- `SW/FW/Source/tests`: host-native unit tests
- `SW/FW/Source/app`: Zephyr or NCS application entry point
- `SW/FW/Source/app/boards`: Nordic DK board-specific config and overlay
- `SW/FW/SKILLS`: firmware workflow skills
- `SW/FW/vendor-sdk`: repo-local SDK area kept intentionally minimal for the Nordic workflow

## Quick Start

From the AirHealth repo root:

```bash
bash SW/FW/SKILLS/fw-init/scripts/run_fw_init.sh
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cmake -S SW/FW/Source -B SW/FW/Source/build
cmake --build SW/FW/Source/build
ctest --test-dir SW/FW/Source/build --output-on-failure
cd SW/FW/vendor-sdk/nordic/ncs
west build -p always -b nrf5340dk/nrf5340/cpuapp ../../../Source/app -d ../../../Source/app/build-nrf5340dk
```

If you run from inside `SW/FW` instead of the AirHealth repo root, the relative `$PWD/SW/FW/...` exports will be wrong and the board build may fail during configure.
