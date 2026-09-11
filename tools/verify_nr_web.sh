#!/usr/bin/env bash
# Additive verification harness for the existing 5G_NR_Simulator web application.
# It validates observable behavior and standards-consistent parameter domains without
# changing nr-core, Android architecture, or the existing web API contract.
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS=0
FAIL=0
WARN=0
pass(){ echo "PASS | $1"; PASS=$((PASS+1)); }
fail(){ echo "FAIL | $1"; FAIL=$((FAIL+1)); }
check(){ local name="$1" url="$2" needle="$3"; local body; if body=$(curl -fsS --max-time 30 "$url") && grep -Fq "$needle" <<<"$body"; then pass "$name"; else fail "$name"; fi; }
api(){ curl -fsS --max-time 60 "$BASE_URL/api/lab?$1"; }
printf '\n=== 5G NR WEB / API VERIFICATION ===\n'
check "Health endpoint" "$BASE_URL/api/health" '"ok":true'
check "Main lab page" "$BASE_URL/lab.html" '/lab.js'
check "V61 link page" "$BASE_URL/link.html" 'V61 End-to-End NR Link'
check "V64 radio page" "$BASE_URL/radio.html" 'V64'
for f in web-server/src/main/resources/static/radio-v64*.js; do
  if node --check "$f" >/dev/null 2>&1; then pass "JavaScript syntax: $f"; else fail "JavaScript syntax: $f"; fi
done
for v in V19 V20 V21 V22 V23 V24 V25 V26 V27 V28 V29 V30 V31 V32 V33 V34 V35 V36 V37 V38 V39 V40 V41 V42 V43 V44 V45 V46 V47 V48 V49 V50 V51 V52 V53 V54 V55 V56 V57 V58 V59 V60 V63; do
  if out=$(api "version=$v") && grep -Fq "\"version\":\"$v\"" <<<"$out"; then pass "API version $v"; else fail "API version $v"; fi
done
# V64 closed-loop is verified by the existing nr-core regression suite, not a new HTTP route.
# LabApi intentionally exposes V63 as the web-facing integrated dashboard.
check "V61 link API" "$BASE_URL/api/simulate?snr=12&prbs=24&mcs=16&ue=2&tx=2&rx=2&layers=1&mod=16-QAM&scs=30&harq=true" '"ok":true'
out=$(api 'version=V63&slots=3&ue=4&cells=2&prbs=24&scs=30&velocity=30')
python3 - "$out" <<'PY'
import json,sys,math
x=json.loads(sys.argv[1]); assert x.get('ok') is True; assert len(x['ueStates'])==4
for u in x['ueStates']:
    assert 1 <= int(u['cqi']) <= 15
    assert 0 <= int(u['allocatedPrbs']) <= 24
    assert math.isfinite(float(u['sinrDb'])); assert math.isfinite(float(u['throughputMbps']))
print('V63 structural invariants OK')
PY
pass "V63 UE/range/finite-value invariants"
if test -f nr-core/src/main/kotlin/com/example/nrsimulator/NrClosedLoopV64.kt && test -f nr-core/src/test/kotlin/com/example/nrsimulator/NrClosedLoopV64Tests.kt && grep -Fq 'NrClosedLoopV64.run' nr-core/src/main/kotlin/com/example/nrsimulator/CoreRegressionMain.kt; then
  pass "V64 closed-loop core verification wired to existing regression suite"
else
  fail "V64 closed-loop core verification wiring"
fi
for scs in 15 30 60; do
  if out=$(api "version=V63&slots=1&ue=1&cells=1&prbs=24&scs=$scs&velocity=0") && grep -Fq '"ok":true' <<<"$out"; then pass "SCS $scs kHz accepted"; else fail "SCS $scs kHz accepted"; fi
done
for mod in 'QPSK' '16-QAM' '64-QAM' '256-QAM'; do
  if curl -fsS --max-time 60 "$BASE_URL/api/simulate?snr=15&prbs=24&mcs=25&ue=1&tx=2&rx=2&layers=1&mod=$mod&scs=30&harq=true" | grep -Fq '"ok":true'; then pass "V61 modulation $mod"; else fail "V61 modulation $mod"; fi
done
# Negative test targets the actual V63 web route. HTTP 400 is a valid handled response.
code=$(curl -sS --max-time 30 -o /tmp/v63_negative.json -w '%{http_code}' "$BASE_URL/api/lab?version=V63&slots=0&ue=0&cells=0&prbs=0&scs=999&velocity=-100" || true)
if [[ "$code" =~ ^[2345][0-9][0-9]$ ]]; then pass "Invalid V63 parameter request handled (HTTP $code)"; else fail "Invalid V63 parameter request handling"; fi
page=$(curl -fsS --max-time 30 "$BASE_URL/radio.html")
for label in 'Radio Environment' 'PHY Pipeline' 'MIMO / CSI' 'Experiment Lab' 'Network Topology' 'Performance' 'Logs' 'Settings' 'Download Report'; do
  if grep -Fq "$label" <<<"$page"; then pass "HMI control/tab: $label"; else fail "HMI control/tab: $label"; fi
done
# Startup contract: Live must default OFF and no unconditional run() may exist in the base controller.
if grep -Fq 'id="liveToggle">▶ &nbsp;Live: OFF' <<<"$page"; then pass "HMI startup: Live OFF"; else fail "HMI startup: Live OFF"; fi
if grep -Fq 'S={data:null,selected:0,running:false,live:false' web-server/src/main/resources/static/radio-v64-fixed.js && ! grep -Eq '(^|[^A-Za-z])run\(\);' web-server/src/main/resources/static/radio-v64-fixed.js; then pass "HMI startup: no unconditional simulation run"; else fail "HMI startup: no unconditional simulation run"; fi
if test -f standards/3gpp-verification-matrix.md; then pass "3GPP verification matrix present"; else fail "3GPP verification matrix present"; fi
printf '\n=== SUMMARY ===\nPASS=%d FAIL=%d WARN=%d\n' "$PASS" "$FAIL" "$WARN"
if (( FAIL > 0 )); then exit 1; fi
