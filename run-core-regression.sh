#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
CORE="$ROOT/nr-core"
OUT="$CORE/build-local"
RES="$CORE/src/main/resources"

# Exact NR LDPC shift tables are required by the V85+ layers. They are normally
# committed under src/main/resources; regenerate them if missing (requires scipy).
if [ ! -f "$RES/nr/ldpc/BG1S0.txt" ]; then
    python3 tools/prepare_nr_ldpc_tables.py
fi

rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t SOURCES < <(find "$CORE/src/main/kotlin" -name '*.kt' ! -name 'CoreRegressionMain.kt' | sort)
kotlinc "${SOURCES[@]}" "$CORE/src/main/kotlin/com/example/nrsimulator/CoreRegressionMain.kt" -include-runtime -d "$OUT/nr-core-regression.jar"
# kotlinc does not package src/main/resources into the jar; add them explicitly.
if [ -d "$RES" ]; then
    (cd "$RES" && jar uf "$OUT/nr-core-regression.jar" .)
fi
# Kotlin's compiler does not guarantee a manifest when several main functions exist in the shared sources.
TMP="$OUT/MANIFEST.MF"
printf 'Main-Class: com.example.nrsimulator.CoreRegressionMainKt\n' > "$TMP"
jar ufm "$OUT/nr-core-regression.jar" "$TMP" >/dev/null
java -jar "$OUT/nr-core-regression.jar"
