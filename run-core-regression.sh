#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
CORE="$ROOT/nr-core"
OUT="$CORE/build-local"
rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t SOURCES < <(find "$CORE/src/main/kotlin" -name '*.kt' ! -name 'CoreRegressionMain.kt' | sort)
kotlinc "${SOURCES[@]}" "$CORE/src/main/kotlin/com/example/nrsimulator/CoreRegressionMain.kt" -include-runtime -d "$OUT/nr-core-regression.jar"
# Kotlin's compiler does not guarantee a manifest when several main functions exist in the shared sources.
TMP="$OUT/MANIFEST.MF"
printf 'Main-Class: com.example.nrsimulator.CoreRegressionMainKt\n' > "$TMP"
jar ufm "$OUT/nr-core-regression.jar" "$TMP" >/dev/null
java -jar "$OUT/nr-core-regression.jar"
