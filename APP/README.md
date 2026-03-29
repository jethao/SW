# AirHealth Mobile App

This folder contains the AirHealth consumer mobile app workspace.

The current baseline scaffold is native-first because the product and architecture documents require iOS and Android support but do not yet pin a cross-platform runtime.

Layout:
- `ios/`: iOS app baseline sources and host-specific setup files
- `android/`: Android app baseline sources and Gradle build files
- `shared/`: cross-platform product contracts, view-model notes, and future shared code only when the repo later establishes a concrete sharing strategy
- `SKILLS/`: Codex mobile workflow skills
- `artifacts/`: local build outputs and packaging artifacts
