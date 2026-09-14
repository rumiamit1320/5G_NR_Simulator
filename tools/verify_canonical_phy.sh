#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORE="$ROOT/nr-core/src/main/kotlin/com/example/nrsimulator"

required=(
  "$CORE/NrCanonicalPhy.kt"
  "$CORE/NrV92V100Additive.kt"
  "$CORE/NrV92V100Tests.kt"
  "$ROOT/web-server/src/main/kotlin/com/example/nrsimulator/web/LabApiV92V100.kt"
)

for f in "${required[@]}"; do
  test -f "$f" || { echo "FAIL | missing $f"; exit 1; }
done

grep -q 'NrCanonicalPhy.components' "$ROOT/web-server/src/main/kotlin/com/example/nrsimulator/web/LabApiV92V100.kt"
grep -q 'V92-V100' "$ROOT/web-server/src/main/kotlin/com/example/nrsimulator/web/LabApiV92V100.kt"

echo "PASS | canonical PHY files and V92-V100 facade present"
