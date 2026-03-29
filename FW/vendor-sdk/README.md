# Firmware Vendor SDK

This directory is the repo-local home for the firmware SDK bootstrap used by `fw-init`.

- Default Nordic SDK install path: `SW/FW/vendor-sdk/nordic/ncs`
- Default helper tooling venv: `SW/FW/vendor-sdk/.venv-fw-init`
- Full SDK contents are intentionally gitignored so the `SW` repo stays syncable

To populate or refresh the SDK here, run the `fw-init` skill or:

```bash
bash /Users/haohua/coding/AirHealth/SW/FW/SKILLS/fw-init/scripts/run_fw_init.sh
```

If you intentionally want to reuse a global SDK cache instead of this repo-local path, set:

```bash
AIRHEALTH_FW_ALLOW_GLOBAL_SDK=1
```
