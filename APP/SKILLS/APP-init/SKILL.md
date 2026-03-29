---
name: app-init
description: Initialize the AirHealth mobile development environment by reading the PRD when available, the latest mobile feature design in `SW/feature-design`, and the latest software architecture spec in `SW/Architecture`, checking whether the local environment supports iOS development, Android development, and package creation, creating the expected app artifact directories under `SW/APP`, and writing the initialization report to `SW/APP/initialize.rpt`.
---

# App Init

Use this skill when Codex needs to bootstrap or verify the local mobile app development environment for AirHealth.

The current AirHealth mobile baseline targets iOS 26+ and Android 16+ consumer phones, uses BLE, and requires packaging support for Apple Health and Health Connect integrations. The source docs do not pin a specific mobile framework, so this skill must not silently assume React Native, Flutter, Expo, or a purely native split unless the repo contents explicitly establish one.

## Required Input

Read these inputs in this order:

- `PM/PRD/PRD.md` when available from the AirHealth root
- latest markdown mobile feature design matching `SW/feature-design/Mobile_Feature_Design_*.md`
- latest markdown architecture spec matching `SW/Architecture/Software_Architecture_Spec_*.md`

Read only when needed:

- existing app manifests or build files under `SW/APP`
- existing environment variables related to Xcode, Android SDK, Java, Node, CocoaPods, or packaging tools
- the current report contents when useful for comparing previous state

Prefer the highest version of each source unless the user explicitly names another file.

## Workflow

Follow this sequence:

1. Read the newest PRD when available, the newest mobile feature design, and the newest architecture spec.
2. Infer the mobile environment baseline from those docs:
   - iOS 26+ development support
   - Android 16+ development support
   - BLE-capable mobile development toolchain
   - Apple Health and Health Connect integration readiness
   - package creation readiness for iOS and Android deliverables
3. Check the local app environment for:
   - `python3`
   - repo-local Python virtualenv for app init helper tooling
   - `xcodebuild`
   - `xcrun`
   - `swift`
   - CocoaPods or Bundler support when an iOS dependency flow needs them
   - `java`
   - Android SDK root and tools such as `sdkmanager`, `adb`, `emulator`, `zipalign`, and `apksigner`
   - Gradle or repo-local `gradlew`
   - optional package-manager tooling such as `node`, `npm`, `yarn`, or `pnpm` only when the repo contents suggest a JS-based app stack
   - optional packaging helpers such as `bundletool`
4. Gather as much local context as possible before any network or installer call:
   - existing app manifests and build files under `SW/APP`
   - existing Xcode selection and SDK paths
   - existing Android SDK, build-tools, and platform-tools locations
   - whether the repo already implies native iOS, native Android, or another app stack
5. If lightweight installable tooling is missing, run [`scripts/run_app_init.sh`](scripts/run_app_init.sh).
6. If network access or writes outside the workspace are required, request escalation before running the script.
7. Always write or refresh the report at `SW/APP/initialize.rpt`.

## Script Use

Prefer the bundled script over ad hoc shell commands:

```bash
./scripts/run_app_init.sh
```

Environment overrides:

- `AIRHEALTH_APP_ANDROID_SDK_ROOT`: preferred Android SDK location
- `AIRHEALTH_APP_TOOLING_VENV`: local Python virtualenv path used for app-init helper tooling
- `AIRHEALTH_APP_ALLOW_BREW_INSTALL=1`: allow Homebrew installation of lightweight missing tools such as CocoaPods or bundletool
- `AIRHEALTH_APP_CREATE_ARTIFACT_DIRS=1`: create or refresh `SW/APP/artifacts/ios` and `SW/APP/artifacts/android`
- `APP_INIT_NO_INSTALL=1`: audit the environment and write the report without installing tools

Default artifact directories:

- `SW/APP/artifacts/ios`
- `SW/APP/artifacts/android`

Default tooling virtualenv:

- `SW/APP/.venv-app-init`

Before downloading or installing anything, consolidate the environment findings first so the init pass can make the smallest necessary set of package-manager calls.

The init pass should always create or reuse a repo-local virtualenv for its own helper tooling, even if no extra Python packages are installed during that run.

If the repo does not yet contain a framework-specific app scaffold, initialize only the shared native toolchain prerequisites and packaging directories. Do not invent a framework or create a full app project unless the user explicitly asks for that next.

## Report Requirements

`SW/APP/initialize.rpt` must include:

- report timestamp
- PRD file used, or that it was unavailable
- mobile feature design file used
- architecture spec file used
- inferred mobile platform baseline
- current environment check results
- tooling virtualenv path and whether it was created or reused
- whether app artifact directories were created or reused
- iOS package creation readiness
- Android package creation readiness
- remaining gaps that still block app development or packaging
- final status: `READY`, `PARTIAL`, or `BLOCKED`

## AirHealth Mobile Mapping

Use these rules unless the source docs say otherwise:

- `iOS 26+` in the PRD or design -> require Xcode command-line tooling and package creation readiness for iOS deliverables
- `Android 16+` in the PRD or design -> require Java plus Android SDK tooling and package creation readiness for Android deliverables
- Apple Health support -> note iOS platform integration readiness
- Health Connect support -> note Android platform integration readiness
- no framework explicitly named -> do not assume React Native, Flutter, Expo, or another cross-platform runtime

If the docs do not support a coherent mobile baseline, stop after environment inspection and mark the report `BLOCKED` with the ambiguity called out explicitly.

## Quality Bar

Before finishing, confirm:

- the newest available mobile design and architecture spec were used
- the PRD was used when available, and its absence is called out when missing
- the script did not silently skip missing native toolchain prerequisites
- the repo-local app-init virtualenv exists or any failure to create it is called out explicitly
- package creation gaps are called out explicitly for both iOS and Android
- no mobile framework was invented without repo evidence or explicit user instruction
- the final report is written to `SW/APP/initialize.rpt`
