#!/usr/bin/env bash
set -euo pipefail
BASE="${1:-http://127.0.0.1:8080}"
python3 - "$BASE" <<'PY'
import json, math, sys, urllib.parse, urllib.request
base=sys.argv[1]
cases=[
 {'payloadBits':128,'snr':20,'modulation':4,'layers':1,'tx':1,'rx':1,'prbs':24,'scs':30,'coding':.5,'equalizer':'MMSE'},
 {'payloadBits':160,'snr':15,'modulation':16,'layers':2,'tx':2,'rx':2,'prbs':52,'scs':30,'coding':.5,'equalizer':'MMSE'},
 {'payloadBits':192,'snr':25,'modulation':64,'layers':2,'tx':4,'rx':4,'prbs':106,'scs':60,'coding':.75,'equalizer':'ZF'},
 {'payloadBits':200,'snr':30,'modulation':256,'layers':1,'tx':2,'rx':2,'prbs':10,'scs':15,'coding':.75,'equalizer':'MMSE'},
]
for i,c in enumerate(cases,1):
    u=base+'/api/link?'+urllib.parse.urlencode(c)
    with urllib.request.urlopen(u,timeout=20) as r: d=json.load(r)
    assert d.get('ok') is True, d
    assert d.get('version')=='V61'
    m=d['metrics']
    for k in ('codedBits','transmittedBits','decodedBits','bitErrors','ber','evmPercent','throughputMbps','qamSymbols','ofdmSize','occupiedSubcarriers'):
        assert isinstance(m[k],(int,float)) and math.isfinite(float(m[k])), (i,k,m[k])
    assert m['codedBits'] >= d['config']['payloadBits']
    assert m['transmittedBits']==m['codedBits']
    assert m['decodedBits']==d['config']['payloadBits']
    assert m['ofdmSize']==256
    assert 0 <= m['ber'] <= 1
    assert m['evmPercent'] >= 0
    assert m['throughputMbps'] > 0
    assert len(d.get('stages',[])) >= 12
    assert d.get('txConstellation') and d.get('rxConstellation')
print(f'V61 end-to-end link validation passed: {len(cases)}/{len(cases)} cases')
PY