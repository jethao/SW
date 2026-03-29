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

The current repository does **not yet** include:

- a Zephyr or NCS application entry point
- a `west.yml` workspace manifest for a board build
- a board configuration
- a flashable MCU image target produced directly from this repo

That means you can set up the environment, build the firmware source and run tests today, but you cannot yet build and flash a real device image from the checked-in source tree alone.

## Environment Setup

### 1. Verify prerequisites

The current firmware environment expects:

- `python3`
- `cmake`
- `ninja`
- `arm-none-eabi-gcc` or `arm-zephyr-eabi-gcc`
- `nrfutil`

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

### 3. Verify environment status

Open:

- `SW/FW/initialize.rpt`

Expected healthy state:

- `Status: READY`

## Build

The current checked-in firmware build is a host-native CMake build for the firmware source library and test executables.

From the AirHealth repo root:

```bash
cmake -S SW/FW/Source -B SW/FW/Source/build
cmake --build SW/FW/Source/build
```

This produces:

- the firmware source static library
- unit-test executables for the modules under `SW/FW/Source/tests`

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

### Current limitation

You cannot currently flash a device image from this repo because there is no checked-in firmware application target that emits a Nordic image such as:

- `zephyr.elf`
- `zephyr.hex`
- `zephyr.bin`
- `merged.hex`

There is also no checked-in `west build` target or board definition in `SW/FW` yet.

### What is needed before flashing works

To support device flashing from this repo, the firmware tree still needs:

1. a real NCS or Zephyr application entry point
2. a board target, for example an `nrf5340` app-core target
3. the associated `prj.conf`, board config, and flash runner setup
4. a documented image output path

### Expected future flash flow

Once a real board app is added, the expected Nordic workflow will look like this:

```bash
west build -b <board-target> <app-dir>
west flash
```

Or, if flashing by artifact:

```bash
nrfutil device program --firmware <image-file>
```

Use those commands only after a real firmware image target is added to the repo.

## Directory Guide

- `SW/FW/Source`: firmware source, build files, and tests
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
```

If you need actual device flashing, add a real NCS board application target to `SW/FW` first, then extend this README with the exact board build and flash commands.
