#!/usr/bin/env python3
"""Regenerate NrLdpcV85EmbeddedTables.kt from the prepared LDPC resources.

Mirrors tools/prepare_nr_ldpc_tables.py's resource format, then embeds each
resource as gzip(level 9, pinned mtime)+base64 in the Kotlin object template.
"""
from pathlib import Path
import base64
import gzip
import sys

from scipy.io import loadmat

SOURCE_REPO = "https://raw.githubusercontent.com/hahaliu2001/python_5gtoolbox"
SOURCE_COMMIT = "fe48630bc0bd9a3d50e579de2c715579d49aaa8e"
EXPECTED = {1: (46, 68, 316), 2: (42, 52, 197)}
GZIP_MTIME = 1789762865  # pinned so the artifact stays byte-identical

OUT = Path("nr-core/src/main/kotlin/com/example/nrsimulator/NrLdpcV85EmbeddedTables.kt")

HEADER = '''package com.example.nrsimulator

/**
 * Embedded gzip+base64 snapshots of the exact NR LDPC shift tables
 * (BG1/BG2, lifting sets 0-7). Source of truth: the generated resources under
 * src/main/resources/nr/ldpc, produced by tools/prepare_nr_ldpc_tables.py from
 * hahaliu2001/python_5gtoolbox (MIT), commit fe48630bc0bd9a3d50e579de2c715579d49aaa8e.
 * These snapshots let a fresh checkout run the V85+ LDPC layers without the
 * python/scipy preparation step (e.g. in container builds).
 */
object NrLdpcV85EmbeddedTables {
    val EMBEDDED: Map<String, String> = mapOf(
'''


def resource_text(mat_path: Path, rows: int, cols: int) -> bytes:
    data = loadmat(mat_path)["BG"]
    if data.shape != (rows, cols):
        raise SystemExit(f"{mat_path.name}: unexpected shape {data.shape}")
    values = [[int(v) for v in row] for row in data.tolist()]
    text = (
        "# Generated from hahaliu2001/python_5gtoolbox (MIT), "
        f"commit {SOURCE_COMMIT}\n"
        f"{rows} {cols}\n"
        + "\n".join(" ".join(str(v) for v in row) for row in values) + "\n"
    )
    return text.encode("utf-8")


def main() -> None:
    import tempfile, urllib.request, shutil
    parts = [HEADER]
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for bg in (1, 2):
            rows, cols, _ = EXPECTED[bg]
            for ils in range(8):
                name = f"BG{bg}S{ils}"
                src = f"{SOURCE_REPO}/{SOURCE_COMMIT}/py5gphy/ldpc/tables/{name}.mat1"
                dst = tmp / f"{name}.mat1"
                with urllib.request.urlopen(src, timeout=60) as s, dst.open("wb") as f:
                    shutil.copyfileobj(s, f)
                blob = gzip.compress(resource_text(dst, rows, cols), compresslevel=9, mtime=GZIP_MTIME)
                b64 = base64.b64encode(blob).decode("ascii")
                parts.append(f'    "{name}" to\n        "{b64}",\n')
    parts.append("    )\n}\n")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("".join(parts), encoding="utf-8")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
