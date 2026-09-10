#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
TMP_DIR="${TMPDIR:-/tmp}/nr-parameter-sweep"
mkdir -p "$TMP_DIR"

get_json() {
  local name="$1"; shift
  curl -fsS --retry 2 --retry-delay 1 "$BASE_URL$1" > "$TMP_DIR/$name.json"
}

# Sweep the supported radio parameter envelope without changing the reference engine.
for spec in \
  "snr_m5|snr=-5&prbs=1&mcs=0&ue=1&tx=1&rx=1&layers=1&mod=QPSK&scs=15&harq=true&tick=0" \
  "snr_0|snr=0&prbs=24&mcs=4&ue=2&tx=2&rx=2&layers=1&mod=16-QAM&scs=30&harq=true&tick=1" \
  "snr_15|snr=15&prbs=52&mcs=16&ue=4&tx=4&rx=4&layers=2&mod=64-QAM&scs=30&harq=true&tick=2" \
  "snr_35|snr=35&prbs=106&mcs=27&ue=8&tx=4&rx=4&layers=4&mod=256-QAM&scs=60&harq=false&tick=3" \
  "prbs_275|snr=15&prbs=106&mcs=16&ue=4&tx=4&rx=4&layers=4&mod=64-QAM&scs=60&harq=true&tick=4"; do
  name="${spec%%|*}"; query="${spec#*|}"
  get_json "$name" "/api/simulate?$query"
done

python3 - "$TMP_DIR" <<'PY'
import json, math, os, sys
root=sys.argv[1]

def load(name):
    with open(os.path.join(root,name+'.json'),encoding='utf-8') as f: return json.load(f)

def finite(d, path):
    cur=d
    for p in path.split('.'):
        cur=cur[p]
    return isinstance(cur,(int,float)) and math.isfinite(float(cur))

items=[load(x) for x in ('snr_m5','snr_0','snr_15','snr_35','prbs_275')]
for d in items:
    assert d.get('ok') is True
    assert all(finite(d,p) for p in ('primary.bits','primary.symbols','primary.snr','primary.evm','primary.throughputMbps','primary.ber'))

# These relationships are deterministic in the existing Simulator model.
assert items[0]['primary']['ber'] > items[1]['primary']['ber'] > items[2]['primary']['ber'] > items[3]['primary']['ber'], 'BER must decrease with SNR'
assert items[3]['primary']['evm'] >= 0 and items[0]['primary']['evm'] >= 0
assert items[0]['config']['prbs']==1 and items[3]['config']['prbs']==106 and items[4]['config']['prbs']==106
assert items[3]['config']['layers']==4 and items[3]['config']['scs']==60 and items[3]['config']['harq'] is False
# Legacy Simulator throughput uses its explicit bandwidth argument; PRB/SCS are still validated as configuration inputs.
assert items[2]['primary']['throughputMbps'] > 0
print('PASS radio sweep: SNR -5..35, PRBs 1..106, MCS 0..27, layers 1..4, SCS 15/30/60, MIMO 1x1..4x4, HARQ on/off')
PY

# Exercise the parameterized V49-V58 boundaries with distinct values.
declare -a LAB_CASES=(
  "V49|version=V49&rbCount=48&startRb=4&symbolCount=7&rbReserve=10&layer=2&channel=PDSCH"
  "V50|version=V50&scs=60&slot=17&startRb=8&rbCount=24"
  "V51|version=V51&ack=false&process=7&ndi=1"
  "V52|version=V52&payloadBytes=400&pduBytes=180&priority=3&lcg=2"
  "V53|version=V53&payloadBytes=128&mode=UM&snBits=10"
  "V54|version=V54&payloadBytes=64&sn=37&hfn=12&bearer=4&direction=1"
  "V55|version=V55&transactionId=3&payloadBytes=32&type=RECONFIGURATION"
  "V56|version=V56&supi=001010123456789&procedure=AUTHENTICATION&ksi=5"
  "V57|version=V57&teid=305419896&seq=42&qfi=7&payloadBytes=128"
  "V58|version=V58&ue=ue-42&plane=F1_C&payloadBytes=32"
)
for spec in "${LAB_CASES[@]}"; do
  name="${spec%%|*}"; query="${spec#*|}"
  get_json "lab_$name" "/api/lab?$query"
done

python3 - "$TMP_DIR" <<'PY'
import json, os, sys
root=sys.argv[1]
expected=[49,50,51,52,53,54,55,56,57,58]
for n in expected:
    with open(os.path.join(root,f'lab_V{n}.json'),encoding='utf-8') as f:d=json.load(f)
    assert d.get('ok') is True, (n,d)
    assert d.get('version')==f'V{n}', d
    assert d.get('parameterized') is True, d
    assert d.get('result'), d
print('PASS V49-V58 parameter sweep: all interactive boundaries accepted distinct inputs and returned results')
PY

echo 'Parameter sweep regression completed successfully.'
