#!/usr/bin/env python3
"""Apply the additive V92-V100 LabApi dispatcher hook without replacing LabApi.kt."""
from pathlib import Path

path = Path("web-server/src/main/kotlin/com/example/nrsimulator/web/LabApi.kt")
s = path.read_text(encoding="utf-8")
needle = '''        if (version in setOf("V85", "V86", "V87", "V88", "V89", "V90", "V91")) {\n            NrV85V91WebAdapter.handle(exchange)\n            return\n        }'''
insert = needle + '''\n        // Additive V92-V100 dispatch: preserve all existing V19-V91 routing.\n        if (version in setOf("V92", "V93", "V94", "V95", "V96", "V97", "V98", "V99", "V100")) {\n            LabApiV92V100.handle(exchange)\n            return\n        }'''
if "LabApiV92V100.handle(exchange)" in s:
    print("V92-V100 dispatch already present")
elif needle not in s:
    raise SystemExit("V85-V91 dispatch block not found; refusing to modify LabApi.kt")
else:
    path.write_text(s.replace(needle, insert, 1), encoding="utf-8")
    print("Applied V92-V100 dispatch")
