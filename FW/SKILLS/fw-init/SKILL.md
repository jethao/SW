---
name: fw-init
description: Initialize the AirHealth firmware development environment by reading the latest electrical design spec in `HW/EE`, inferring the main CPU or MCU and vendor SDK, checking whether the local environment already supports that target, pulling the proper vendor SDK when support is missing, and writing the initialization report to `SW/FW/initialize.rpt`.
---

# Firmware Init

Use this skill when Codex needs to bootstrap or verify the local firmware development environment for AirHealth.

The current AirHealth hardware baseline uses a Nordic nRF5340-class BLE SoC or MCU in the latest EE design spec, so the expected vendor SDK is Nordic nRF Connect SDK unless a newer EE spec changes the target.

## Required Input

Read the latest markdown EE design spec matching:

- `HW/EE/EE_Design_Spec_v*.md`

Read only when needed:

- existing firmware manifests or toolchain files under `SW/FW`
- existing environment variables related to firmware toolchains or SDK paths

Prefer the highest EE design spec version unless the user explicitly names another file.

## Workflow

Follow this sequence:

1. Read the newest EE design spec and identify the main CPU or MCU family, vendor, and expected SDK family.
2. Check the local firmware environment for target support:
   - existing vendor SDK checkout
   - `python3`
   - `west`
   - `cmake`
   - `ninja`
   - ARM cross-compiler such as `arm-zephyr-eabi-gcc` or `arm-none-eabi-gcc`
   - optional vendor helpers such as `nrfutil`
3. If the vendor SDK is missing, run [`scripts/run_fw_init.sh`](scripts/run_fw_init.sh).
4. If network access or writes outside the workspace are required, request escalation before running the script.
5. Always write or refresh the report at `SW/FW/initialize.rpt`.

## Script Use

Prefer the bundled script over ad hoc shell commands:

```bash
./scripts/run_fw_init.sh
```

Environment overrides:

- `AIRHEALTH_FW_SDK_DIR`: preferred SDK install or reuse path
- `AIRHEALTH_FW_SDK_REF`: explicit vendor SDK tag, branch, or revision to install
- `AIRHEALTH_FW_TOOLING_VENV`: local Python virtualenv path used for helper tooling such as `west`
- `AIRHEALTH_FW_ALLOW_GLOBAL_SDK=1`: allow reusing a global SDK cache outside `SW/FW`
- `FW_INIT_NO_INSTALL=1`: audit the environment and write the report without downloading an SDK

Default install location:

- `SW/FW/vendor-sdk/nordic/ncs`

Default tooling virtualenv:

- `SW/FW/vendor-sdk/.venv-fw-init`

The script should prefer the repo-local SDK path so the firmware environment lives under `SW/FW`. The `vendor-sdk` folder is intended to be gitignored except for lightweight bootstrap files and documentation, so the full vendor SDK does not need to be committed to Git. Reuse a global SDK cache only when `AIRHEALTH_FW_ALLOW_GLOBAL_SDK=1` is set.

## Report Requirements

`SW/FW/initialize.rpt` must include:

- report timestamp
- EE design spec file used
- inferred main CPU or MCU
- inferred vendor and SDK family
- current environment check results
- whether an SDK checkout was reused or installed
- SDK path and selected SDK revision when known
- remaining gaps that still block firmware development
- final status: `READY`, `PARTIAL`, or `BLOCKED`

## AirHealth MCU Mapping

Use these rules unless the EE spec says otherwise:

- `nRF5340` or `Nordic` -> Nordic -> nRF Connect SDK
- `STM32` -> STMicroelectronics -> STM32Cube
- `ESP32` or `Espressif` -> Espressif -> ESP-IDF

If the EE spec does not map cleanly to one of these families, stop after environment inspection and mark the report `BLOCKED` with the ambiguity called out explicitly.

## Quality Bar

Before finishing, confirm:

- the newest EE design spec was used
- the inferred MCU and SDK match the hardware document
- the script did not silently skip missing prerequisites
- the report names any missing compiler or build-tool gaps even if the SDK was installed
- the final report is written to `SW/FW/initialize.rpt`
