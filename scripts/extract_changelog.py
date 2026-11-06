#!/usr/bin/env python3
"""Print the CHANGELOG.md section for a given versionName (e.g. 1.0.0.26)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("usage: extract_changelog.py <version>")
    ver = sys.argv[1].strip()
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    pat = rf"(## {re.escape(ver)}\b.*?)(?=\n## |\Z)"
    m = re.search(pat, text, re.S)
    if m:
        print(m.group(1).strip())
    else:
        print(f"## {ver}\n\n（CHANGELOG.md 中暂无该版本条目）")


if __name__ == "__main__":
    main()
