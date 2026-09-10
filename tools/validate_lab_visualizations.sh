#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TMP_DIR="${TMPDIR:-/tmp}/nr-lab-viz"
mkdir -p "$TMP_DIR"

curl -fsS "$BASE_URL/lab.html" > "$TMP_DIR/lab.html"
curl -fsS "$BASE_URL/lab.js" > "$TMP_DIR/lab.js"

python3 - "$TMP_DIR/lab.html" "$TMP_DIR/lab.js" <<'PY'
from pathlib import Path
import sys
html = Path(sys.argv[1]).read_text(encoding='utf-8')
js = Path(sys.argv[2]).read_text(encoding='utf-8')
checks = {
    'lab page loads': '<title>5G NR Simulator — V19–V60 Lab</title>' in html,
    'educational readout text': 'How to read it:' in js,
    'visualization renderer': 'function visualization(version, card)' in js,
    'V19 visualization': "version==='V19'" in js,
    'V30 visualization': "version==='V30'" in js,
    'V19–V30 parameter adapter': 'api/lab' in js,
    'V31–V60 extension script': '/lab-v31-v60.js' in html,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if failed:
    raise SystemExit('Visualization validation failed: ' + ', '.join(failed))
PY

for version in V19 V22 V29; do
  curl -fsS "$BASE_URL/api/lab?version=$version&snr=15&prbs=52&layers=2&mcs=16" > "$TMP_DIR/$version.json"
  python3 - "$TMP_DIR/$version.json" "$version" <<'PY'
import json, sys
path, version = sys.argv[1:]
d = json.load(open(path, encoding='utf-8'))
assert d.get('ok') is True, f'{version}: ok=false'
assert d.get('version') == version, f'{version}: wrong version'
print(f'PASS: {version} lab endpoint')
PY
done

echo "Educational visualization and lab endpoint validation passed."
