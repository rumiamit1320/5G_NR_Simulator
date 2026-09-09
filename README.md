# 5G NR Simulator

Android 5G NR simulation project with a shared pure-Kotlin NR core and Android UI.

## Architecture

- `app/` — Android application/UI
- `nr-core/` — pure Kotlin/JVM NR engine
- V1–V60 — existing simulator/conformance layers
- Phase 1–6 — PHY verification, UE↔gNB, 5GS SA, simulation engine, KAT harness, dashboard

## Build

The repository is intended to be built with Gradle. GitHub Actions is configured to build the Android debug APK.

> The current connected GitHub interface can write UTF-8 repository files but cannot upload the local project archive/binary tree in one operation. The full source package remains available from the ChatGPT workspace; the repository bootstrap below is therefore prepared first.
