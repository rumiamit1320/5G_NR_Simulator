#!/usr/bin/env python3
"""Prepare exact NR LDPC shift tables for V85.

The source tables are MIT-licensed data files from hahaliu2001/python_5gtoolbox.
They are pinned to a commit for reproducibility and converted from MATLAB
.mat1 files into plain text resources consumed by NrLdpcV85.
"""
from pathlib import Path
from urllib.request import urlopen
import hashlib
import shutil
import tempfile

try:
    from scipy.io import loadmat
except ImportError as exc:
    raise SystemExit("scipy is required: python3 -m pip install scipy") from exc

SOURCE_REPO = "https://raw.githubusercontent.com/hahaliu2001/python_5gtoolbox"
SOURCE_COMMIT = "fe48630bc0bd9a3d50e579de2c715579d49aaa8e"
OUT = Path("nr-core/src/main/resources/nr/ldpc")

EXPECTED = {1: (46, 68, 316), 2: (42, 52, 197)}


def fetch(url: str, dst: Path) -> None:
    with urlopen(url, timeout=30) as src, dst.open("wb") as out:
        shutil.copyfileobj(src, out)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = []
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for bg in (1, 2):
            rows, cols, edges = EXPECTED[bg]
            for ils in range(8):
                name = f"BG{bg}S{ils}"
                src = f"{SOURCE_REPO}/{SOURCE_COMMIT}/py5gphy/ldpc/tables/{name}.mat1"
                mat_path = tmp / f"{name}.mat1"
                fetch(src, mat_path)
                data = loadmat(mat_path)["BG"]
                if data.shape != (rows, cols):
                    raise SystemExit(f"{name}: unexpected shape {data.shape}")
                values = [[int(v) for v in row] for row in data.tolist()]
                count = sum(v >= 0 for row in values for v in row)
                if count != edges:
                    raise SystemExit(f"{name}: expected {edges} edges, got {count}")
                out_path = OUT / f"{name}.txt"
                out_path.write_text(
                    "# Generated from hahaliu2001/python_5gtoolbox (MIT), "
                    f"commit {SOURCE_COMMIT}\n"
                    f"{rows} {cols}\n" +
                    "\n".join(" ".join(str(v) for v in row) for row in values) + "\n",
                    encoding="utf-8",
                )
                digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
                manifest.append(f"{name} {digest}")

    (OUT / "MANIFEST.txt").write_text(
        "V85 exact NR LDPC table resources\n"
        f"source_commit={SOURCE_COMMIT}\n" + "\n".join(manifest) + "\n",
        encoding="utf-8",
    )
    print(f"Prepared {len(manifest)} exact NR LDPC tables in {OUT}")


if __name__ == "__main__":
    main()
