#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TMP_DIR="${TMPDIR:-/tmp}/nr-scenarios"
mkdir -p "$TMP_DIR"

run_case() {
  local name="$1"
  local query="$2"
  local file="$TMP_DIR/$name.json"
  echo "--- scenario: $name ---"
  curl -fsS --retry 2 --retry-delay 1 "$BASE_URL/api/simulate?$query" > "$file"
  python3 - "$file" "$name" <<'PY'
import json, math, sys
path, name = sys.argv[1:]
with open(path, encoding='utf-8') as f:
    d = json.load(f)
assert d.get('ok') is True, f'{name}: API did not return ok=true'
for section in ('config', 'primary', 'advanced', 'phy'):
    assert section in d, f'{name}: missing {section}'
for key in ('bits', 'symbols', 'snr', 'evm', 'throughputMbps', 'ber'):
    value = d['primary'].get(key)
    assert value is not None, f'{name}: missing primary.{key}'
    assert isinstance(value, (int, float)), f'{name}: primary.{key} is not numeric'
    assert math.isfinite(float(value)), f'{name}: primary.{key} is not finite'
print(f"PASS {name}: {d['config']['mod']} / SCS {d['config']['scs']} kHz / {d['config']['prbs']} PRBs / {d['config']['layers']} layers")
PY
}

run_case "low_snr_qpsk" "snr=0&prbs=24&mcs=4&ue=2&tx=2&rx=2&layers=1&mod=QPSK&scs=15&harq=true&tick=1"
run_case "mid_64qam" "snr=15&prbs=52&mcs=16&ue=4&tx=4&rx=4&layers=2&mod=64-QAM&scs=30&harq=true&tick=2"
run_case "high_256qam" "snr=30&prbs=106&mcs=27&ue=8&tx=4&rx=4&layers=4&mod=256-QAM&scs=60&harq=false&tick=3"
run_case "conservative_16qam" "snr=8&prbs=10&mcs=9&ue=1&tx=1&rx=2&layers=1&mod=16-QAM&scs=30&harq=true&tick=4"

echo "All parameterized end-to-end web simulation scenarios passed."
