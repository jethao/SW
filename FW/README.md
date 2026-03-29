# AirHealth Firmware

## Overview

This directory contains the AirHealth firmware source tree, firmware workflow skills, environment bootstrap assets, and initialization reports.

Current firmware code lives under:

- `SW/FW/Source`

Current SDK bootstrap assets live under:

- `SW/FW/vendor-sdk`

Current environment report lives at:

- `SW/FW/initialize.rpt`

## Current Repo Status

The current repository supports:

- local firmware environment bootstrap for the Nordic `nRF5340` class target
- host-native CMake builds for the firmware source library
- host-native unit tests for firmware modules under `SW/FW/Source/tests`

The current repository now includes:

- a Zephyr or NCS application entry point under `SW/FW/Source/app`
- an initial Nordic board target for `nrf5340dk/nrf5340/cpuapp`
- a board-specific config and overlay
- a flashable MCU image target produced by `west build`

The current repository still does **not yet** include:

- a repo-local `west.yml` manifest for the application itself
- a custom production board definition for AirHealth hardware

That means you can set up the environment, build the firmware source, run tests, and build or flash an initial Nordic DK image today.

## Environment Setup

### 1. Verify prerequisites

The current firmware environment expects:

- `python3`
- `cmake`
- `ninja`
- `nrfutil`
- a Zephyr SDK installation for board builds, for example `SW/FW/vendor-sdk/zephyr-sdk-0.17.4`

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
- writes the environment report to `SW/FW/initialize.rpt`

Default repo-local SDK paths:

- SDK checkout: `SW/FW/vendor-sdk/nordic/ncs`
- tooling virtualenv: `SW/FW/vendor-sdk/.venv-fw-init`
- recommended Zephyr SDK path for board builds: `SW/FW/vendor-sdk/zephyr-sdk-0.17.4`

### 3. Verify environment status

Open:

- `SW/FW/initialize.rpt`

Expected healthy state:

- `Status: READY`

## Build

The firmware tree supports both:

- a host-native CMake build for shared firmware modules and tests
- a Zephyr or NCS board build for a real Nordic application image

From the AirHealth repo root:

```bash
cmake -S SW/FW/Source -B SW/FW/Source/build
cmake --build SW/FW/Source/build
```

This produces:

- the firmware source static library
- unit-test executables for the modules under `SW/FW/Source/tests`

### Zephyr or NCS board build

The initial checked-in board target is:

- `nrf5340dk/nrf5340/cpuapp`

From the AirHealth repo root:

```bash
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cd SW/FW/vendor-sdk/nordic/ncs
west build -p always -b nrf5340dk/nrf5340/cpuapp ../../../Source/app -d ../../../Source/app/build-nrf5340dk
```

This produces a real device image under:

- `SW/FW/Source/app/build-nrf5340dk/app/zephyr/zephyr.elf`
- `SW/FW/Source/app/build-nrf5340dk/app/zephyr/zephyr.hex`
- `SW/FW/Source/app/build-nrf5340dk/merged.hex`

## Run Tests

From the AirHealth repo root:

```bash
ctest --test-dir SW/FW/Source/build --output-on-failure
```

This runs the current firmware test suite, including:

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

## Flash To Device

After the board build succeeds, flash from the NCS workspace root:

```bash
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cd SW/FW/vendor-sdk/nordic/ncs
west flash -d ../../../Source/app/build-nrf5340dk
```

If you prefer flashing by explicit artifact:

```bash
cd /Users/haohua/coding/AirHealth
nrfutil device program --firmware SW/FW/Source/app/build-nrf5340dk/merged.hex
```

Expected prerequisites:

- the board is connected over USB
- Nordic debug access is available on the host
- the `fw-init` flow has already populated `SW/FW/vendor-sdk`
- the Zephyr SDK bundle is available at `SW/FW/vendor-sdk/zephyr-sdk-0.17.4` or equivalent

## Directory Guide

- `SW/FW/Source`: shared firmware source, build files, and tests
- `SW/FW/Source/app`: Zephyr or NCS application entry point and board config
- `SW/FW/SKILLS`: firmware workflow skills used by Codex
- `SW/FW/vendor-sdk`: repo-local SDK bootstrap area
- `SW/FW/initialize.rpt`: firmware environment status report
- `SW/FW/fw-mgr.rpt`: firmware manager workflow report

## Quick Start

From the AirHealth repo root:

```bash
bash SW/FW/SKILLS/fw-init/scripts/run_fw_init.sh
cmake -S SW/FW/Source -B SW/FW/Source/build
cmake --build SW/FW/Source/build
ctest --test-dir SW/FW/Source/build --output-on-failure
export PATH="$PWD/SW/FW/vendor-sdk/.venv-fw-init/bin:$PATH"
export ZEPHYR_TOOLCHAIN_VARIANT=zephyr
export ZEPHYR_SDK_INSTALL_DIR="$PWD/SW/FW/vendor-sdk/zephyr-sdk-0.17.4"
cd SW/FW/vendor-sdk/nordic/ncs
west build -p always -b nrf5340dk/nrf5340/cpuapp ../../../Source/app -d ../../../Source/app/build-nrf5340dk
```
