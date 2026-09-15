# V92-V100 integration

Run `python3 tools/integrate_v92_v100_labapi.py` from the repository root. The script performs a guarded, single-occurrence insertion immediately after the existing V85-V91 dispatch in `LabApi.kt` and refuses to modify the file if that anchor is absent.

Expected inserted route:

```kotlin
if (version in setOf("V92", "V93", "V94", "V95", "V96", "V97", "V98", "V99", "V100")) {
    LabApiV92V100.handle(exchange)
    return
}
```

This keeps V19-V91 routing intact and does not modify `nr-core`.
